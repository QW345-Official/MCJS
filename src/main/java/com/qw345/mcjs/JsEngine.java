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
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;

public final class JsEngine {
    private static final ConfigManager CONFIG = ConfigManager.getInstance();
    private static final NashornScriptEngineFactory FACTORY = new NashornScriptEngineFactory();

    private static final ExecutorService JS_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "js-command-executor");
        t.setDaemon(true);
        return t;
    });

    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String SHIM =
        "var runCommand = function(c){ return JsApi.runCommand(c); };\n"
        + "var registerCommand = function(n,l,f){ \n"
        + "  if (typeof l === 'function') { f = l; l = 2; }\n"
        + "  return JsApi.registerCommand(n,l,f);\n"
        + "};\n"
        + "var saveFile = function(p,c){ return JsApi.saveFile(p,c); };\n"
        + "var saveBinaryFile = function(p,c){ return JsApi.saveBinaryFile(p,c); };\n"
        + "var loadFile = function(p){ return JsApi.loadFile(p); };\n"
        + "var setOption = function(k,v){ return JsApi.setOption(k,v); };\n"
        + "var getOption = function(k){ return JsApi.getOption(k); };\n"
        + "var resetOptions = function(){ return JsApi.resetOptions(); };\n";

    private JsEngine() {}

    public static void executeAsync(CommandSourceStack source, String script) {
        int maxLen = CONFIG.getInt("codeLenLimit", 10000);
        long timeout = CONFIG.getLong("codeTimeLimit", 5000);

        final String scriptText = script == null ? "" : script;
        if (scriptText.length() > maxLen) {
            if (CONFIG.getBoolean("errDisplay", true)) {
                source.sendSystemMessage(Component.literal("[js] ERROR: script exceeds max length " + maxLen)
                    .withStyle(ChatFormatting.RED));
            }
            return;
        }

        final String code = SHIM + scriptText;
        final long start = System.currentTimeMillis();

        Future<?> future = JS_EXECUTOR.submit(() -> {
            JsExecutionContext ctx = new JsExecutionContext(source, source.getServer());
            JsExecutionContext.set(ctx);
            try {
                ScriptEngine engine = FACTORY.getScriptEngine();
                ctx.engine = engine;
                engine.put("JsApi", new JsApi());

                try {
                    Object result = engine.eval(code);
                    onDone(source, ctx, scriptText, "OK",
                        result == null ? "(undefined)" : String.valueOf(result), start);
                } catch (ScriptException e) {
                    onDone(source, ctx, scriptText, "ERROR", rootMessage(e), start);
                } catch (Throwable t) {
                    onDone(source, ctx, scriptText, "ERROR", rootMessage(t), start);
                }
            } catch (Throwable t) {
                try {
                    onDone(source, null, scriptText, "ERROR", "Internal error: " + t, start);
                } catch (Exception ignored) {}
            } finally {
                JsExecutionContext.clear();
            }
        });


        Thread watchdog = new Thread(() -> {
            try {
                long elapsed = 0;
                long checkInterval = 100;
                while (elapsed < timeout && !future.isDone()) {
                    Thread.sleep(checkInterval);
                    elapsed += checkInterval;
                }
                if (!future.isDone()) {
                    future.cancel(true);
                    Path logFile = source.getServer()
                        .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                        .resolve(ExampleMod.MOD_ID).resolve("js_execution.log");
                    appendLog(logFile, source, scriptText, "TIMEOUT",
                        "exceeded " + timeout + "ms", System.currentTimeMillis() - start);
                    if (CONFIG.getBoolean("errDisplay", true)) {
                        source.getServer().execute(() -> source.sendSystemMessage(
                            Component.literal("[js] ERROR: script timed out after " + timeout + "ms")
                                .withStyle(ChatFormatting.RED)));
                    }
                }
            } catch (InterruptedException ignored) {
            }
        }, "js-command-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private static void onDone(CommandSourceStack source, JsExecutionContext ctx, String script,
            String status, String message, long start) {
        long ms = System.currentTimeMillis() - start;
        boolean isError = !"OK".equals(status);

        if (CONFIG.getBoolean("logExecution", true)) {
            Path logDir = ctx != null ? ctx.modDataDir :
                source.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve(ExampleMod.MOD_ID);
            appendLog(logDir.resolve("js_execution.log"), source, script, status, message, ms);
        }

        boolean showInfo = CONFIG.getBoolean("infoDisplay", true);
        boolean showErr = CONFIG.getBoolean("errDisplay", true);

        if ((isError && showErr) || (!isError && showInfo)) {
            source.getServer().execute(() -> {
                Component line = isError
                    ? Component.literal("[js] " + status + ": " + message).withStyle(ChatFormatting.RED)
                    : Component.literal("[js] " + message).withStyle(ChatFormatting.WHITE);
                source.sendSystemMessage(line);
            });
        }
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

    public static void invokeRegistered(Object engineObj, String globalName,
                                        CommandSourceStack source, String args) {
        ScriptEngine engine = (ScriptEngine) engineObj;
        JsExecutionContext ctx = new JsExecutionContext(source, source.getServer());
        boolean showInfo = CONFIG.getBoolean("customCommandInfo", true);

        JS_EXECUTOR.submit(() -> {
            JsExecutionContext.set(ctx);
            try {
                String arg1 = "";
                String customname = "";
                String raw = args == null ? "" : args;
                int idx = -1;
                for (int i = 0; i < raw.length(); i++) {
                    if (Character.isWhitespace(raw.charAt(i))) { idx = i; break; }
                }
                if (idx < 0) {
                    arg1 = raw;
                } else {
                    arg1 = raw.substring(0, idx);
                    customname = raw.substring(idx + 1);
                }
                String playerName = source.getTextName();

                Object result = ((Invocable) engine).invokeFunction(
                    globalName, playerName, arg1, customname);

                String r = result == null ? "(undefined)" : String.valueOf(result);
                if (showInfo) {
                    deliver(source, "[" + globalName + "] " + r, false);
                }
            } catch (Throwable t) {
                if (CONFIG.getBoolean("errDisplay", true)) {
                    deliver(source, "ERROR: " + rootMessage(t), true);
                }
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
        if (cur != null && cur.getMessage() != null) {
            return cur.getMessage();
        }
        return String.valueOf(cur != null ? cur : t);
    }
}