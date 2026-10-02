package com.naglyadno.nedra.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.naglyadno.nedra.NedraMod;
import com.naglyadno.nedra.guide.GuideBook;
import com.naglyadno.nedra.pressure.PressureManager;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * /nedra guide  - выдать ещё один справочник
 * /nedra info   - текущая глубина, давление и защита
 * /nedra reload - перечитать config/nedra.json (только для операторов)
 */
public final class NedraCommands {

	private NedraCommands() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("nedra")
				.then(CommandManager.literal("guide").executes(context -> giveGuide(context.getSource())))
				.then(CommandManager.literal("info").executes(context -> info(context.getSource())))
				.then(CommandManager.literal("reload")
						.requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
						.executes(context -> reload(context.getSource()))));
	}

	private static int giveGuide(ServerCommandSource source) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		ItemStack book = GuideBook.create();
		if (!player.getInventory().insertStack(book)) {
			player.dropItem(book, false);
		}
		source.sendFeedback(() -> Text.translatable("command.nedra.guide.given"), false);
		return 1;
	}

	private static int info(ServerCommandSource source) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		PressureManager manager = NedraMod.pressureManager();
		if (manager == null) {
			return 0;
		}
		PressureManager.Reading r = manager.read(player);
		if (!r.active()) {
			source.sendFeedback(() -> Text.translatable("command.nedra.info.inactive").formatted(Formatting.GRAY), false);
			return 1;
		}
		Text tier = Text.translatable("hud.nedra.tier." + r.tier().level);
		source.sendFeedback(() -> Text.translatable("command.nedra.info",
				player.getBlockY(),
				Math.round(r.effective()),
				Math.round(r.raw()),
				r.protection(),
				tier), false);
		return 1;
	}

	private static int reload(ServerCommandSource source) {
		NedraMod.reloadConfig(source.getServer());
		source.sendFeedback(() -> Text.translatable("command.nedra.reload.done").formatted(Formatting.GREEN), true);
		return 1;
	}
}
