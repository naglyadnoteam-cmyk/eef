package com.naglyadno.fatecards.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.naglyadno.fatecards.FateCardsMod;
import com.naglyadno.fatecards.game.GameManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Команды /fatecards start | stop | status | minplayers | interval. Без ограничения по правам — для игры с друзьями. */
public final class FateCardsCommand {

	private FateCardsCommand() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("fatecards")
				.then(CommandManager.literal("start").executes(FateCardsCommand::start))
				.then(CommandManager.literal("stop").executes(FateCardsCommand::stop))
				.then(CommandManager.literal("status").executes(FateCardsCommand::status))
				.then(CommandManager.literal("minplayers")
						.then(CommandManager.argument("count", IntegerArgumentType.integer(2))
								.executes(FateCardsCommand::minPlayers)))
				.then(CommandManager.literal("interval")
						.then(CommandManager.argument("seconds", IntegerArgumentType.integer(30))
								.executes(FateCardsCommand::interval)))
		);
	}

	private static int start(CommandContext<ServerCommandSource> ctx) {
		GameManager manager = FateCardsMod.getGameManager();
		if (manager == null) {
			ctx.getSource().sendError(Text.literal("Fate Cards ещё не готов."));
			return 0;
		}
		ctx.getSource().sendFeedback(() -> manager.start(), true);
		return 1;
	}

	private static int stop(CommandContext<ServerCommandSource> ctx) {
		GameManager manager = FateCardsMod.getGameManager();
		if (manager == null) {
			ctx.getSource().sendError(Text.literal("Fate Cards ещё не готов."));
			return 0;
		}
		ctx.getSource().sendFeedback(() -> manager.stop(), true);
		return 1;
	}

	private static int status(CommandContext<ServerCommandSource> ctx) {
		GameManager manager = FateCardsMod.getGameManager();
		if (manager == null) {
			ctx.getSource().sendError(Text.literal("Fate Cards ещё не готов."));
			return 0;
		}
		ctx.getSource().sendFeedback(() -> Text.literal("Состояние: " + manager.state().name()).formatted(Formatting.AQUA), false);
		return 1;
	}

	private static int minPlayers(CommandContext<ServerCommandSource> ctx) {
		GameManager manager = FateCardsMod.getGameManager();
		if (manager == null) {
			ctx.getSource().sendError(Text.literal("Fate Cards ещё не готов."));
			return 0;
		}
		int count = IntegerArgumentType.getInteger(ctx, "count");
		manager.config().minPlayers = count;
		ctx.getSource().sendFeedback(() -> Text.literal("Минимум игроков: " + count).formatted(Formatting.GREEN), true);
		return 1;
	}

	private static int interval(CommandContext<ServerCommandSource> ctx) {
		GameManager manager = FateCardsMod.getGameManager();
		if (manager == null) {
			ctx.getSource().sendError(Text.literal("Fate Cards ещё не готов."));
			return 0;
		}
		int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
		manager.config().roundIntervalSeconds = seconds;
		ctx.getSource().sendFeedback(() -> Text.literal("Интервал раундов: " + seconds + "с").formatted(Formatting.GREEN), true);
		return 1;
	}
}
