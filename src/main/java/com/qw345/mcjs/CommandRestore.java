package com.qw345.mcjs;

import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

public final class CommandRestore {
    private CommandRestore() {}

    public static void replayAll(MinecraftServer server) {
        Map<String, String> all = CommandPersistence.loadAll();
        if (all.isEmpty()) {
            ExampleMod.LOGGER.info("[js] no persisted commands to replay");
            return;
        }

        CommandSourceStack console = server.createCommandSourceStack();

        Thread replay = new Thread(() -> {
            for (Map.Entry<String, String> e : all.entrySet()) {
                String script = e.getValue();
                try {
                    JsEngine.executeSync(console, script);
                    ExampleMod.LOGGER.info("[js] replayed command: {}", e.getKey());
                } catch (Throwable t) {
                    ExampleMod.LOGGER.warn("[js] replay failed for {}: {}", e.getKey(), t.toString());
                }
            }
            ExampleMod.LOGGER.info("[js] replay done, {} commands", all.size());
        }, "js-command-replay");
        replay.setDaemon(true);
        replay.start();
    }
}