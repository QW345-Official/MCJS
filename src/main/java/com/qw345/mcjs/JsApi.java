package com.qw345.mcjs;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.MinecraftServer;


public class JsApi {
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
		return JsCommand.registerDynamicCommand(ctx, name, fn);
	}


	public Object saveFile(String path, String content) {
		JsExecutionContext ctx = JsExecutionContext.current();
		if (ctx == null) {
			return "ERROR: no active execution context";
		}
		if (path == null || content == null) {
			return "ERROR: path and content are required";
		}
		try {
			Path root = ctx.modDataDir.toAbsolutePath().normalize();
			Files.createDirectories(root);
			Path target = root.resolve(path).normalize();
			if (!target.startsWith(root)) {
				return "ERROR: path escapes mod data directory (traversal blocked)";
			}
			Files.createDirectories(target.getParent());
			Files.writeString(target, content, StandardCharsets.UTF_8);
			return "SAVED: " + path;
		} catch (Exception e) {
			return "ERROR: " + e;
		}
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
