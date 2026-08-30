package com.qw345.mcjs;

import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("MCJS-Config");
    private static final ConfigManager INSTANCE = new ConfigManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, Object> options = new ConcurrentHashMap<>();
    private Path configPath;

    private ConfigManager() {
        // 默认值
        options.put("infoDisplay", true);
        options.put("errDisplay", true);
        options.put("customCommandInfo", true);
        options.put("codeLenLimit", 10000);
        options.put("codeTimeLimit", 5000);
        options.put("currentCodeLimit", 1);
        options.put("logExecution", true);
        options.put("allowBinaryFiles", true);
        options.put("maxFileSize", 10485760); // 10MB
    }

    public static ConfigManager getInstance() {
        return INSTANCE;
    }

    public void init(Path modDir) {
        this.configPath = modDir.resolve("config.json");
        load();
    }

    @SuppressWarnings("unchecked")
    public void load() {
        if (configPath == null || !configPath.toFile().exists()) {
            save();
            return;
        }
        try (FileReader reader = new FileReader(configPath.toFile())) {
            Map<String, Object> loaded = GSON.fromJson(reader, Map.class);
            if (loaded != null) {
                // 只覆盖已存在的键，保留默认值
                for (Map.Entry<String, Object> entry : loaded.entrySet()) {
                    if (options.containsKey(entry.getKey())) {
                        options.put(entry.getKey(), entry.getValue());
                    }
                }
            }
            LOGGER.info("Config loaded from {}", configPath);
        } catch (Exception e) {
            LOGGER.warn("Failed to load config: {}", e.getMessage());
        }
    }

    public void save() {
        if (configPath == null) return;
        try {
            Path parent = configPath.getParent();
            if (parent != null) {
                java.nio.file.Files.createDirectories(parent);
            }
            try (FileWriter writer = new FileWriter(configPath.toFile())) {
                GSON.toJson(options, writer);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to save config: {}", e.getMessage());
        }
    }

    public String setOption(String key, Object value) {
        if (!options.containsKey(key)) {
            return "ERROR: unknown option '" + key + "'";
        }
        // 类型检查
        Object current = options.get(key);
        if (current instanceof Boolean && !(value instanceof Boolean)) {
            if (value instanceof String) {
                String s = ((String) value).toLowerCase();
                if (s.equals("true") || s.equals("false")) {
                    value = Boolean.parseBoolean(s);
                } else {
                    return "ERROR: option '" + key + "' requires boolean value";
                }
            } else {
                return "ERROR: option '" + key + "' requires boolean value";
            }
        }
        if (current instanceof Number && !(value instanceof Number)) {
            if (value instanceof String) {
                try {
                    value = Integer.parseInt((String) value);
                } catch (NumberFormatException e) {
                    try {
                        value = Long.parseLong((String) value);
                    } catch (NumberFormatException e2) {
                        return "ERROR: option '" + key + "' requires numeric value";
                    }
                }
            } else {
                return "ERROR: option '" + key + "' requires numeric value";
            }
        }
        // 特殊验证
        if (key.equals("codeLenLimit") && ((Number) value).intValue() < 1) {
            return "ERROR: codeLenLimit must be >= 1";
        }
        if (key.equals("codeTimeLimit") && ((Number) value).intValue() < 100) {
            return "ERROR: codeTimeLimit must be >= 100ms";
        }
        if (key.equals("currentCodeLimit") && ((Number) value).intValue() < 1) {
            return "ERROR: currentCodeLimit must be >= 1";
        }
        if (key.equals("maxFileSize") && ((Number) value).intValue() < 1024) {
            return "ERROR: maxFileSize must be >= 1024 bytes";
        }
        options.put(key, value);
        save();
        return "OK: " + key + " = " + value;
    }

    public Object getOption(String key) {
        return options.get(key);
    }

    public void reset() {
        options.put("infoDisplay", true);
        options.put("errDisplay", true);
        options.put("customCommandInfo", true);
        options.put("codeLenLimit", 10000);
        options.put("codeTimeLimit", 5000);
        options.put("currentCodeLimit", 1);
        options.put("logExecution", true);
        options.put("allowBinaryFiles", true);
        options.put("maxFileSize", 10485760);
        save();
    }

    public boolean getBoolean(String key, boolean def) {
        Object v = options.get(key);
        if (v instanceof Boolean) return (Boolean) v;
        return def;
    }

    public int getInt(String key, int def) {
        Object v = options.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        return def;
    }

    public long getLong(String key, long def) {
        Object v = options.get(key);
        if (v instanceof Number) return ((Number) v).longValue();
        return def;
    }
}