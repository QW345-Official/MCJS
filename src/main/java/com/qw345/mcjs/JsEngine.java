package com.qw345.mcjs;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.script.Invocable;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;


public final class JsEngine {
	public static final int MAX_SCRIPT_LENGTH = 10000;
	/** 100 tick = 100/20 秒 = 5000 ms。 */
	public static final long TIMEOUT_MS = 1000L * 50L;

	private static final NashornScriptEngineFactory FACTORY = new NashornScriptEngineFactory();


	private static final ExecutorService JS_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "js-command-executor");
		t.setDaemon(true);
		return t;
	});
	private static final java.util.concurrent.atomic.AtomicBoolean RUNNING =
		new java.util.concurrent.atomic.AtomicBoolean(false);

	private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");


	private static final String SHIM =
		"var runCommand = function(c){ return JsApi.runCommand(c); };\n"
		+ "var registerCommand = function(n,f){ return JsApi.registerCommand(n,f); };\n"
		+ "var saveFile = function(p,c){ return JsApi.saveFile(p,c); };\n";

	private JsEngine() {
	}


	public static void executeAsync(CommandSourceStack source, String script) {
		final String scriptText = script == null ? "" : script;
		if (scriptText.length() > MAX_SCRIPT_LENGTH) {
			source.sendSystemMessage(Component.literal("[js] ERROR: script exceeds max length " + MAX_SCRIPT_LENGTH)
				.withStyle(ChatFormatting.RED));
			return;
		}
		if (!RUNNING.compareAndSet(false, true)) {
			source.sendSystemMessage(Component.literal("[js] ERROR: another script is already running (stack_size=1)")
				.withStyle(ChatFormatting.RED));
			return;
		}

		final String code = SHIM + scriptText;
		final long start = System.currentTimeMillis();
		Future<?> future = JS_EXECUTOR.submit(() -> {

			Thread.interrupted();
			JsExecutionContext ctx = new JsExecutionContext(source, source.getServer());
			JsExecutionContext.set(ctx);
			try {
				ScriptEngine engine = FACTORY.getScriptEngine();
				ctx.engine = engine;
				engine.put("JsApi", new JsApi());
				Object result = engine.eval(code);
				onDone(source, ctx, scriptText, "OK", result == null ? "(undefined)" : String.valueOf(result), start);
			} catch (ScriptException e) {
				onDone(source, ctx, scriptText, "ERROR", rootMessage(e), start);
			} catch (Throwable t) {
				onDone(source, ctx, scriptText, "ERROR", rootMessage(t), start);
			} finally {
				JsExecutionContext.clear();
				RUNNING.set(false);
			}
		});


		Thread watchdog = new Thread(() -> {
			try {
				future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
			} catch (TimeoutException e) {
				future.cancel(true);
				Path logFile = source.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
					.resolve(ExampleMod.MOD_ID).resolve("js_execution.log");
				appendLog(logFile, source, scriptText, "TIMEOUT",
					"exceeded " + TIMEOUT_MS + "ms", System.currentTimeMillis() - start);
				source.getServer().execute(() -> source.sendSystemMessage(
					Component.literal("[js] ERROR: script timed out after " + TIMEOUT_MS + "ms")
						.withStyle(ChatFormatting.RED)));
			} catch (ExecutionException | InterruptedException e) {

			}
		}, "js-command-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
	}


	private static void onDone(CommandSourceStack source, JsExecutionContext ctx, String script,
			String status, String message, long start) {
		long ms = System.currentTimeMillis() - start;
		appendLog(ctx.modDataDir.resolve("js_execution.log"), source, script, status, message, ms);
		source.getServer().execute(() -> {
			Component line = status.equals("OK")
				? Component.literal("[js] " + message)
				: Component.literal("[js] " + status + ": " + message).withStyle(ChatFormatting.RED);
			source.sendSystemMessage(line);
		});
	}


	private static void appendLog(Path logFile, CommandSourceStack source, String script,
			String status, String message, long ms) {
		String line = "[" + LocalDateTime.now().format(LOG_TIME) + "] source=" + source.getTextName()
			+ " status=" + status + " duration=" + ms + "ms message=" + message + "\nscript>>>\n" + script + "\n";
		ExampleMod.LOGGER.info("[js] {}", line);
		try {
			if (logFile != null) {
				Files.createDirectories(logFile.getParent());
				Files.writeString(logFile, line, StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			}
		} catch (Exception e) {
			ExampleMod.LOGGER.warn("[js] failed to write execution log: {}", e.toString());
		}
	}


	public static void invokeRegistered(Object engineObj, String globalName, CommandSourceStack source) {
		ScriptEngine engine = (ScriptEngine) engineObj;
		JsExecutionContext ctx = new JsExecutionContext(source, source.getServer());
		JS_EXECUTOR.submit(() -> {
			Thread.interrupted();
			JsExecutionContext.set(ctx);
			try {
				Object result = ((Invocable) engine).invokeFunction(globalName, new Object[]{ source.getTextName() });
				String r = result == null ? "(undefined)" : String.valueOf(result);
				deliver(source, "[" + globalName + "] " + r, false);
			} catch (Throwable t) {
				deliver(source, "ERROR: " + rootMessage(t), true);
			} finally {
				JsExecutionContext.clear();
			}
		});
	}


	private static void deliver(CommandSourceStack source, String msg, boolean error) {
		source.getServer().execute(() -> source.sendSystemMessage(
			Component.literal("[js] " + msg).withStyle(error ? ChatFormatting.RED : ChatFormatting.WHITE)));
	}

	private static String rootMessage(Throwable t) {
		Throwable cur = t;
		if (cur instanceof ExecutionException) {
			cur = cur.getCause();
		}
		if (cur instanceof ScriptException && cur.getCause() != null) {
			cur = cur.getCause();
		}
		return String.valueOf(cur);
	}
}
