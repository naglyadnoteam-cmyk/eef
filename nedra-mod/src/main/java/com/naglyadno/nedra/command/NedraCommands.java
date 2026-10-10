package com.naglyadno.nedra.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.naglyadno.nedra.NedraMod;
import com.naglyadno.nedra.guide.GuideBook;
import com.naglyadno.nedra.pressure.PressureManager;
import com.naglyadno.nedra.worldgen.deep.Citadels;
import com.naglyadno.nedra.worldgen.deep.FrozenCaverns;
import com.naglyadno.nedra.worldgen.deep.DeepLocator;
import com.naglyadno.nedra.worldgen.deep.DeepTerrain;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * /nedra guide  - выдать ещё один справочник
 * /nedra info   - текущая глубина, давление и защита
 * /nedra locate settlement|river|waterfall|citadel|frozen_caverns|lush_pocket|echo_hollows|magnetic_caverns|crystal_depths|overgrown_depths|scarlet_grottoes
 *     - найти место в недрах (операторы)
 * /nedra reload - перечитать config/nedra.json (только для операторов)
 */
public final class NedraCommands {

	private NedraCommands() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("nedra")
				.then(CommandManager.literal("guide").executes(context -> giveGuide(context.getSource())))
				.then(CommandManager.literal("info").executes(context -> info(context.getSource())))
				.then(CommandManager.literal("locate")
						.requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
						.then(CommandManager.literal("settlement").executes(context -> locate(context.getSource(), "settlement")))
						.then(CommandManager.literal("river").executes(context -> locate(context.getSource(), "river")))
						.then(CommandManager.literal("echo_hollows").executes(context -> locate(context.getSource(), "echo_hollows")))
						.then(CommandManager.literal("magnetic_caverns").executes(context -> locate(context.getSource(), "magnetic_caverns")))
						.then(CommandManager.literal("crystal_depths").executes(context -> locate(context.getSource(), "crystal_depths")))
						.then(CommandManager.literal("overgrown_depths").executes(context -> locate(context.getSource(), "overgrown_depths")))
						.then(CommandManager.literal("scarlet_grottoes").executes(context -> locate(context.getSource(), "scarlet_grottoes")))
						.then(CommandManager.literal("lush_pocket").executes(context -> locate(context.getSource(), "lush_pocket")))
						.then(CommandManager.literal("waterfall").executes(context -> locate(context.getSource(), "waterfall")))
						.then(CommandManager.literal("citadel").executes(context -> locate(context.getSource(), "citadel")))
						.then(CommandManager.literal("frozen_caverns").executes(context -> locate(context.getSource(), "frozen_caverns"))))
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

	/** Поиск мест недр по детерминированной форме глубин - без генерации чанков, поэтому мгновенно. */
	private static int locate(ServerCommandSource source, String what) {
		long seed = source.getWorld().getSeed();
		BlockPos from = BlockPos.ofFloored(source.getPosition());
		BlockPos found = switch (what) {
			case "settlement" -> DeepLocator.settlement(seed, from.getX(), from.getZ());
			case "river" -> DeepLocator.river(seed, from.getY() < -170 ? 2 : 1, from.getX(), from.getZ());
			case "echo_hollows" -> DeepLocator.layer(seed, DeepTerrain.Layer.ECHO, from.getX(), from.getZ());
			case "magnetic_caverns" -> DeepLocator.layer(seed, DeepTerrain.Layer.MAGNETIC, from.getX(), from.getZ());
			case "overgrown_depths" -> DeepLocator.layer(seed, DeepTerrain.Layer.JUNGLE, from.getX(), from.getZ());
			case "scarlet_grottoes" -> DeepLocator.layer(seed, DeepTerrain.Layer.SCARLET, from.getX(), from.getZ());
			case "lush_pocket" -> DeepLocator.lush(seed, from.getX(), from.getZ());
			case "waterfall" -> {
				DeepLocator.View view = DeepLocator.waterfall(seed, from.getY() < -170 ? 2 : 1, from.getX(), from.getZ());
				yield view == null ? null : view.pos();
			}
			case "citadel" -> {
				Citadels.Site site = DeepLocator.citadel(seed, from.getX(), from.getZ());
				yield site == null ? null : DeepLocator.citadelEntrance(site);
			}
			case "frozen_caverns" -> {
				FrozenCaverns.Site site = FrozenCaverns.nearest(source.getServer().getOverworld(), from.getX(), from.getZ(), 8);
				yield site == null ? null : FrozenCaverns.viewPoint(source.getServer().getOverworld(), site);
			}
			default -> DeepLocator.layer(seed, DeepTerrain.Layer.CRYSTAL, from.getX(), from.getZ());
		};
		boolean place = what.equals("settlement") || what.equals("river") || what.equals("lush_pocket")
				|| what.equals("waterfall") || what.equals("citadel");
		Text name = Text.translatable(place ? "command.nedra.locate." + what : "biome.nedra." + what);
		if (found == null) {
			source.sendError(Text.translatable("command.nedra.locate.none", name));
			return 0;
		}
		int distance = (int) Math.round(Math.sqrt(from.getSquaredDistance(found)));
		source.sendFeedback(() -> Text.translatable("command.nedra.locate.found", name,
				found.getX(), found.getY(), found.getZ(), distance).formatted(Formatting.GREEN), false);
		return 1;
	}

	private static int reload(ServerCommandSource source) {
		NedraMod.reloadConfig(source.getServer());
		source.sendFeedback(() -> Text.translatable("command.nedra.reload.done").formatted(Formatting.GREEN), true);
		return 1;
	}
}
