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
import java.util.concurrent.Semaphore;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.script.Invocable;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;

public final class JsEngine {
    private static final ConfigManager CONFIG = ConfigManager.getInstance();
    private static final NashornScriptEngineFactory FACTORY = new NashornScriptEngineFactory();
    private static final Semaphore EXECUTION_SEMAPHORE = new Semaphore(1);
    private static volatile int currentConcurrentExecutions = 0;

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
        int maxConcurrent = CONFIG.getInt("currentCodeLimit", 1);

        final String scriptText = script == null ? "" : script;
        if (scriptText.length() > maxLen) {
            if (CONFIG.getBoolean("errDisplay", true)) {
                source.sendSystemMessage(Component.literal("[js] ERROR: script exceeds max length " + maxLen)
                    .withStyle(ChatFormatting.RED));
            }
            return;
        }

        // 并发控制
        synchronized (JsEngine.class) {
            if (currentConcurrentExecutions >= maxConcurrent) {
                if (CONFIG.getBoolean("errDisplay", true)) {
                    source.sendSystemMessage(Component.literal("[js] ERROR: max concurrent scripts reached (" + maxConcurrent + ")")
                        .withStyle(ChatFormatting.RED));
                }
                return;
            }
            currentConcurrentExecutions++;
        }

        final String code = SHIM + scriptText;
        final long start = System.currentTimeMillis();
        
        Future<?> future = JS_EXECUTOR.submit(() -> {
            try {
                JsExecutionContext ctx = new JsExecutionContext(source, source.getServer());
                JsExecutionContext.set(ctx);
                try {
                    ScriptEngine engine = FACTORY.getScriptEngine();
                    ctx.engine = engine;
                    engine.put("JsApi", new JsApi());
                    
                    // 使用更严格的超时控制
                    final Thread execThread = Thread.currentThread();
                    Future<?> timeoutFuture = JS_EXECUTOR.submit(() -> {
                        try {
                            Thread.sleep(timeout);
                            execThread.interrupt();
                        } catch (InterruptedException ignored) {}
                    });
                    
                    try {
                        Object result = engine.eval(code);
                        timeoutFuture.cancel(true);
                        onDone(source, ctx, scriptText, "OK", result == null ? "(undefined)" : String.valueOf(result), start);
                    } catch (ScriptException e) {
                        timeoutFuture.cancel(true);
                        onDone(source, ctx, scriptText, "ERROR", rootMessage(e), start);
                    } catch (Throwable t) {
                        timeoutFuture.cancel(true);
                        onDone(source, ctx, scriptText, "ERROR", rootMessage(t), start);
                    }
                } finally {
                    JsExecutionContext.clear();
                    synchronized (JsEngine.class) {
                        currentConcurrentExecutions--;
                    }
                }
            } catch (Throwable t) {
                // 捕获所有异常防止线程崩溃
                try {
                    onDone(source, null, scriptText, "ERROR", "Internal error: " + t, start);
                } catch (Exception ignored) {}
                synchronized (JsEngine.class) {
                    currentConcurrentExecutions--;
                }
            }
        });

        // 使用独立的监控线程，避免超时导致"Software caused connection abort"
        Thread watchdog = new Thread(() -> {
            try {
                // 使用较短的检查间隔，以便更及时响应中断
                long elapsed = 0;
                long checkInterval = 100;
                while (elapsed < timeout && !future.isDone()) {
                    Thread.sleep(checkInterval);
                    elapsed += checkInterval;
                }
                if (!future.isDone()) {
                    future.cancel(true);
                    Path logFile = source.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                        .resolve(ExampleMod.MOD_ID).resolve("js_execution.log");
                    appendLog(logFile, source, scriptText, "TIMEOUT",
                        "exceeded " + timeout + "ms", System.currentTimeMillis() - start);
                    if (CONFIG.getBoolean("errDisplay", true)) {
                        source.getServer().execute(() -> source.sendSystemMessage(
                            Component.literal("[js] ERROR: script timed out after " + timeout + "ms")
                                .withStyle(ChatFormatting.RED)));
                    }
                    synchronized (JsEngine.class) {
                        if (currentConcurrentExecutions > 0) currentConcurrentExecutions--;
                    }
                }
            } catch (InterruptedException e) {
                // 正常结束
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

        // 发送消息，根据配置决定是否显示
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

    public static void invokeRegistered(Object engineObj, String globalName, CommandSourceStack source) {
        ScriptEngine engine = (ScriptEngine) engineObj;
        JsExecutionContext ctx = new JsExecutionContext(source, source.getServer());
        boolean showInfo = CONFIG.getBoolean("customCommandInfo", true);
        
        JS_EXECUTOR.submit(() -> {
            JsExecutionContext.set(ctx);
            try {
                Object result = ((Invocable) engine).invokeFunction(globalName, new Object[]{ source.getTextName() });
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