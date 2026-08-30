package com.qw345.mcjs;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExampleMod implements ModInitializer {
    public static final String MOD_ID = "mcjs";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {

        
        JsCommand.register();
        

        
        LOGGER.info("MCJS loaded: /js <script>");
        LOGGER.info("Use /js set <key> <value> to configure options");
        LOGGER.info("Available options: infoDisplay, errDisplay, customCommandInfo, codeLenLimit, codeTimeLimit, currentCodeLimit");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}