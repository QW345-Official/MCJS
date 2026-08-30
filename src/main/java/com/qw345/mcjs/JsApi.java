package com.qw345.mcjs;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.CommandSourceStack;

public class JsApi {
    private static final ConfigManager CONFIG = ConfigManager.getInstance();

    public String runCommand(String command) {
        JsExecutionContext ctx = JsExecutionContext.current();
        if (ctx == null) {
            return "ERROR: no active execution context";
        }
        MinecraftServer server = ctx.server;
        if (command == null || command.isBlank()) {
            return "ERROR: empty command";
        }
        CompletableFuture<String> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                server.getCommands().performPrefixedCommand(ctx.source, command);
                future.complete("SUCCESS");
            } catch (Throwable t) {
                future.complete("ERROR: " + t);
            }
        });
        return awaitServer(future);
    }

    public Object registerCommand(String name, Object fn) {
        JsExecutionContext ctx = JsExecutionContext.current();
        if (ctx == null) {
            return "ERROR: no active execution context";
        }
        if (name == null || !name.matches("[a-zA-Z0-9_.:-]{1,64}")) {
            return "ERROR: invalid command name (use letters/digits/_/./:/-)";
        }
        // 默认权限等级为2
        int requiredLevel = 2;
        return JsCommand.registerDynamicCommand(ctx, name, fn, requiredLevel);
    }

    public Object registerCommand(String name, int level, Object fn) {
        JsExecutionContext ctx = JsExecutionContext.current();
        if (ctx == null) {
            return "ERROR: no active execution context";
        }
        if (name == null || !name.matches("[a-zA-Z0-9_.:-]{1,64}")) {
            return "ERROR: invalid command name (use letters/digits/_/./:/-)";
        }
        if (level < 0 || level > 4) {
            return "ERROR: permission level must be 0-4";
        }
        return JsCommand.registerDynamicCommand(ctx, name, fn, level);
    }

    public Object saveFile(String path, String content) {
        return saveBinaryFile(path, content.getBytes(StandardCharsets.UTF_8));
    }

    public Object saveBinaryFile(String path, Object content) {
        JsExecutionContext ctx = JsExecutionContext.current();
        if (ctx == null) {
            return "ERROR: no active execution context";
        }
        if (path == null || content == null) {
            return "ERROR: path and content are required";
        }
        try {
            byte[] data;
            if (content instanceof String) {
                data = ((String) content).getBytes(StandardCharsets.UTF_8);
            } else if (content instanceof byte[]) {
                data = (byte[]) content;
            } else {
                // 尝试Base64解码
                try {
                    data = Base64.getDecoder().decode(content.toString());
                } catch (IllegalArgumentException e) {
                    return "ERROR: content must be string, byte[], or base64 encoded";
                }
            }
            
            Path root = ctx.modDataDir.toAbsolutePath().normalize();
            Files.createDirectories(root);
            Path target = root.resolve(path).normalize();
            if (!target.startsWith(root)) {
                return "ERROR: path escapes mod data directory (traversal blocked)";
            }
            Files.createDirectories(target.getParent());
            Files.write(target, data);
            return "SAVED: " + path + " (" + data.length + " bytes)";
        } catch (Exception e) {
            return "ERROR: " + e;
        }
    }

    public Object loadFile(String path) {
        JsExecutionContext ctx = JsExecutionContext.current();
        if (ctx == null) {
            return "ERROR: no active execution context";
        }
        if (path == null) {
            return "ERROR: path is required";
        }
        try {
            Path root = ctx.modDataDir.toAbsolutePath().normalize();
            Path target = root.resolve(path).normalize();
            if (!target.startsWith(root)) {
                return "ERROR: path escapes mod data directory (traversal blocked)";
            }
            if (!Files.exists(target)) {
                return "ERROR: file not found: " + path;
            }
            byte[] data = Files.readAllBytes(target);
            

            long maxSize = CONFIG.getLong("maxFileSize", 10485760);
            if (data.length > maxSize) {
                return "ERROR: file size (" + data.length + " bytes) exceeds limit (" + maxSize + " bytes)";
            }
            

            try {
                String text = new String(data, StandardCharsets.UTF_8);

                if (isText(text)) {
                    return text;
                }
            } catch (Exception e) {

            }
            return Base64.getEncoder().encodeToString(data);
        } catch (Exception e) {
            return "ERROR: " + e;
        }
    }

    private static boolean isText(String s) {
        for (char c : s.toCharArray()) {
            if (c < 0x20 && c != '\n' && c != '\r' && c != '\t') {
                return false;
            }
        }
        return true;
    }

    public String setOption(String key, Object value) {
        return CONFIG.setOption(key, value);
    }

    public Object getOption(String key) {
        return CONFIG.getOption(key);
    }

    public String resetOptions() {
        CONFIG.reset();
        return "Options reset to defaults";
    }

    private static String awaitServer(CompletableFuture<String> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "ERROR: interrupted (script timeout)";
        } catch (Exception e) {
            return "ERROR: " + e;
        }
    }
}