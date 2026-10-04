package com.qw345.mcjs;

import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

public final class CommandRestore {
    private CommandRestore() {}

    public static void restoreAll(MinecraftServer server) {
        Map<String, String> all = CommandPersistence.loadAll();
        if (all.isEmpty()) {
            ExampleMod.LOGGER.info("[js] no persisted commands to restore");
            return;
        }

        // 用控制台 source 执行恢复脚本
        CommandSourceStack source = server.createCommandSourceStack();

        StringBuilder sb = new StringBuilder();
        for (String line : all.values()) {
            sb.append(line).append('\n');
        }

        ExampleMod.LOGGER.info("[js] restoring {} persisted commands", all.size());
        JsEngine.executeAsync(source, sb.toString());
    }
}