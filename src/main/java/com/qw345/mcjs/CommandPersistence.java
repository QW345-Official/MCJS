package com.qw345.mcjs;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;


public final class CommandPersistence {

    private static final Map<String, String> CACHE = new LinkedHashMap<>();
    private static Path filePath;

    private CommandPersistence() {}

    public static synchronized void init(Path configDir) {
        filePath = configDir.resolve("registercommands.js");
        CACHE.clear();
        try {
            Files.createDirectories(configDir);
        } catch (Exception e) {
            ExampleMod.LOGGER.warn("[js] failed to create config dir: {}", e.toString());
        }
        load();
        if (!Files.exists(filePath)) {
            try {
                Files.writeString(filePath,
                    "// MCJS 持久化命令注册文件。由 mod 自动维护，手动编辑请谨慎。\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE);
            } catch (Exception e) {
                ExampleMod.LOGGER.warn("[js] failed to create registercommands.js: {}", e.toString());
            }
        }
    }


    public static synchronized void saveOrUpdate(JsExecutionContext ctx,
                                                 String name, int level, String fnSource) {
        if (filePath == null && ctx != null) {
            filePath = ctx.configDir.resolve("registercommands.js");
        }
        if (filePath == null) return;

        String normalized = normalizeFnSource(fnSource);
        String line = "registerCommand('" + escape(name) + "', " + level + ", " + normalized + ");";
        CACHE.put(name, line);
        writeAll();
    }

    public static synchronized void remove(String name) {
        CACHE.remove(name);
        writeAll();
    }

    public static synchronized Map<String, String> loadAll() {
        return new LinkedHashMap<>(CACHE);
    }


    private static void load() {
        if (filePath == null || !Files.exists(filePath)) return;
        try {
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            int idx = 0;
            while (true) {
                int start = content.indexOf("registerCommand", idx);
                if (start < 0) break;

                int open = content.indexOf('(', start);
                if (open < 0) break;


                int q1 = content.indexOf('\'', open);
                if (q1 < 0) { idx = open + 1; continue; }
                int q2 = content.indexOf('\'', q1 + 1);
                if (q2 < 0) { idx = q1 + 1; continue; }
                String name = content.substring(q1 + 1, q2);


                int fnStart = content.indexOf("function", q2);
                if (fnStart < 0) { idx = q2 + 1; continue; }


                String fnSrc = extractBalanced(content, fnStart);
                if (fnSrc == null) { idx = fnStart + 1; continue; }


                int semi = content.indexOf(';', fnStart + fnSrc.length());
                if (semi < 0) semi = fnStart + fnSrc.length();

                String fullLine = content.substring(start, semi + 1).trim();
                CACHE.put(name, fullLine);

                idx = semi + 1;
            }
            ExampleMod.LOGGER.info("[js] loaded {} persisted commands from {}", CACHE.size(), filePath);
        } catch (Exception e) {
            ExampleMod.LOGGER.warn("[js] failed to load registercommands.js: {}", e.toString());
        }
    }


    private static String extractBalanced(String content, int fnStart) {
        int brace = content.indexOf('{', fnStart);
        if (brace < 0) return null;
        int depth = 0;
        for (int i = brace; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return content.substring(fnStart, i + 1);
                }
            }
        }
        return null;
    }

    private static void writeAll() {
        if (filePath == null) return;
        try {
            Files.createDirectories(filePath.getParent());
            StringBuilder sb = new StringBuilder();
            sb.append("// MCJS 持久化命令注册文件。由 mod 自动维护，手动编辑请谨慎。\n");
            for (String line : CACHE.values()) {
                sb.append(line).append('\n');
            }
            Files.writeString(filePath, sb.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            ExampleMod.LOGGER.warn("[js] failed to write registercommands.js: {}", e.toString());
        }
    }

    private static String normalizeFnSource(String src) {
        return src.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }
}