package com.qw345.mcjs;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

public final class JsCommand {
	private record RegisteredHandler(Object engine, String globalName) {
	}

	private static final Map<String, RegisteredHandler> REGISTERED = new ConcurrentHashMap<>();

	private JsCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(Commands.literal("js")
				.requires(Commands.hasPermission(2))
				.executes(ctx -> {
					help(ctx.getSource());
					return 1;
				})
				.then(Commands.literal("list")
					.executes(ctx -> list(ctx.getSource())))
				.then(Commands.argument("script", StringArgumentType.greedyString())
					.executes(ctx -> runScript(ctx.getSource(), StringArgumentType.getString(ctx, "script"))))
			)
		);
	}

	private static int runScript(CommandSourceStack source, String script) {
		JsEngine.executeAsync(source, script);
		return 1;
	}

	private static int help(CommandSourceStack source) {
		source.sendSuccess(
			() -> Component.literal("/js <脚本> — 执行JavaScript; /js list — 列出已注册的动态命令"),
			false);
		return 1;
	}

	private static int list(CommandSourceStack source) {
		String names = REGISTERED.isEmpty()
			? "(none)"
			: String.join(", ", REGISTERED.keySet().stream().map(n -> "/" + n).toList());
		source.sendSuccess(() -> Component.literal("[js] registered commands: " + names), false);
		return 1;
	}

	public static Object registerDynamicCommand(JsExecutionContext ctx, String name, Object fn) {
		MinecraftServer server = ctx.server;
		CompletableFuture<Object> future = new CompletableFuture<>();
		server.execute(() -> {
			try {
				String globalName = "__js_reg_" + name;
				ctx.engine.put(globalName, fn);
				REGISTERED.put(name, new RegisteredHandler(ctx.engine, globalName));
				server.getCommands().getDispatcher().register(
					Commands.literal(name)
						.requires(Commands.hasPermission(2))
						.executes(c -> invokeRegistered(c.getSource(), name))
				);
				future.complete("REGISTERED: /" + name);
			} catch (Throwable t) {
				future.complete("ERROR: " + t);
			}
		});
		try {
			return future.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return "ERROR: interrupted (script timeout)";
		} catch (Exception e) {
			return "ERROR: " + e;
		}
	}

	/** 触发动态命令时调用(服务端主线程),把处理器丢给 JS 单线程执行后立即返回,不阻塞主线程。 */
	private static int invokeRegistered(CommandSourceStack source, String name) {
		RegisteredHandler h = REGISTERED.get(name);
		if (h == null) {
			source.sendSystemMessage(Component.literal("[js] ERROR: command no longer registered")
				.withStyle(ChatFormatting.RED));
			return 0;
		}
		JsEngine.invokeRegistered(h.engine(), h.globalName(), source);
		return 1;
	}
}
