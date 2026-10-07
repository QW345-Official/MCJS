package com.qw345.mcjs;

import java.nio.file.Path;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExampleMod implements ModInitializer {
    public static final String MOD_ID = "mcjs";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static volatile MinecraftServer SERVER;
    private static volatile Path CONFIG_DIR;

    @Override
    public void onInitialize() {
        JsCommand.register();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            SERVER = server;
            CONFIG_DIR = server.getServerDirectory().resolve("config").resolve(MOD_ID);

            ConfigManager.getInstance().init(CONFIG_DIR);
            CommandPersistence.init(CONFIG_DIR);


            server.execute(() -> CommandRestore.replayAll(server));
        });

        LOGGER.info("MCJS loaded: /js <script>");
    }

    public static MinecraftServer server() {
        return SERVER;
    }

    public static Path configDir() {
        return CONFIG_DIR;
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}