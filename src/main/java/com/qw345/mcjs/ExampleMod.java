package com.qw345.mcjs;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExampleMod implements ModInitializer {
    public static final String MOD_ID = "mcjs";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        JsCommand.register();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            java.nio.file.Path configDir =
                server.getServerDirectory().resolve("config").resolve(MOD_ID);

            ConfigManager.getInstance().init(configDir);
            CommandPersistence.init(configDir);
            CommandRestore.restoreAll(server);
        });

        LOGGER.info("MCJS loaded: /js <script>");
        LOGGER.info("Use /js set <key> <value> to configure options");
        LOGGER.info("Available options: infoDisplay, errDisplay, customCommandInfo, codeLenLimit, codeTimeLimit, currentCodeLimit");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}