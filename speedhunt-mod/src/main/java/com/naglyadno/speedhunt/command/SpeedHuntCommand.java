package com.naglyadno.speedhunt.command;

import com.mojang.brigadier.CommandDispatcher;
import com.naglyadno.speedhunt.SpeedHuntMod;
import com.naglyadno.speedhunt.game.GameManager;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Команды /speedhunt start | stop | status. */
public final class SpeedHuntCommand {

	private SpeedHuntCommand() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("speedhunt")
				.then(CommandManager.literal("start")
						.requires(source -> source.hasPermissionLevel(2))
						.executes(SpeedHuntCommand::start))
				.then(CommandManager.literal("stop")
						.requires(source -> source.hasPermissionLevel(2))
						.executes(SpeedHuntCommand::stop))
				.then(CommandManager.literal("status")
						.executes(SpeedHuntCommand::status))
		);
	}

	private static int start(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx) {
		GameManager manager = SpeedHuntMod.getGameManager();
		if (manager == null) {
			ctx.getSource().sendError(Text.literal("Speedhunt ещё не готов."));
			return 0;
		}
		ctx.getSource().sendFeedback(() -> manager.start(), true);
		return 1;
	}

	private static int stop(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx) {
		GameManager manager = SpeedHuntMod.getGameManager();
		if (manager == null) {
			ctx.getSource().sendError(Text.literal("Speedhunt ещё не готов."));
			return 0;
		}
		ctx.getSource().sendFeedback(() -> manager.stop(), true);
		return 1;
	}

	private static int status(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx) {
		GameManager manager = SpeedHuntMod.getGameManager();
		if (manager == null) {
			ctx.getSource().sendError(Text.literal("Speedhunt ещё не готов."));
			return 0;
		}
		ctx.getSource().sendFeedback(() -> Text.literal("Состояние матча: " + manager.state().name())
				.formatted(Formatting.AQUA), false);
		return 1;
	}
}
