package com.qw345.mcjs;

import java.nio.file.Path;
import javax.script.ScriptEngine;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

public final class JsExecutionContext {
	private static final ThreadLocal<JsExecutionContext> CURRENT = new ThreadLocal<>();

	public final CommandSourceStack source;
	public final MinecraftServer server;
	public final Path modDataDir;


	public ScriptEngine engine;

	public JsExecutionContext(CommandSourceStack source, MinecraftServer server) {
		this.source = source;
		this.server = server;

		this.modDataDir = server.getWorldPath(LevelResource.ROOT).resolve(ExampleMod.MOD_ID);
	}

	public static JsExecutionContext current() {
		return CURRENT.get();
	}

	public static void set(JsExecutionContext ctx) {
		CURRENT.set(ctx);
	}

	public static void clear() {
		CURRENT.remove();
	}
}
