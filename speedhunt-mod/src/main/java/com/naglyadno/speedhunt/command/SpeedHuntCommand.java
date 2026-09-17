package com.naglyadno.speedhunt.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.naglyadno.speedhunt.SpeedHuntMod;
import com.naglyadno.speedhunt.game.GameManager;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Команды /speedhunt start | stop | status | minplayers. */
public final class SpeedHuntCommand {

	private SpeedHuntCommand() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		// Намеренно без requires(hasPermissionLevel(...)): мод рассчитан на игру с
		// друзьями в обычном мире (в т.ч. Open to LAN без читов), где ни один из
		// игроков обычно не является оператором. Если нужно ограничить запуск
		// матча только операторами на публичном сервере — верните
		// .requires(source -> source.hasPermissionLevel(2)) на start/stop.
		dispatcher.register(CommandManager.literal("speedhunt")
				.then(CommandManager.literal("start")
						.executes(SpeedHuntCommand::start))
				.then(CommandManager.literal("stop")
						.executes(SpeedHuntCommand::stop))
				.then(CommandManager.literal("status")
						.executes(SpeedHuntCommand::status))
				.then(CommandManager.literal("minplayers")
						.then(CommandManager.argument("count", IntegerArgumentType.integer(2))
								.executes(SpeedHuntCommand::minPlayers)))
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

	private static int minPlayers(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx) {
		GameManager manager = SpeedHuntMod.getGameManager();
		if (manager == null) {
			ctx.getSource().sendError(Text.literal("Speedhunt ещё не готов."));
			return 0;
		}
		int count = IntegerArgumentType.getInteger(ctx, "count");
		ctx.getSource().sendFeedback(() -> manager.setMinPlayers(count), true);
		return 1;
	}
}
