package com.naglyadno.prostor.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.datafixers.util.Pair;
import com.naglyadno.prostor.worldgen.Lakes;
import com.naglyadno.prostor.worldgen.ProstorBiomes;
import net.minecraft.command.CommandSource;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.util.Locale;
import java.util.Set;

/**
 * /prostor tp &lt;биом&gt;      - перенестись в ближайший биом мода (на берег озера, лицом к воде)
 * /prostor locate &lt;биом&gt;  - показать координаты ближайшего биома
 * /prostor locate_all      - найти все биомы мода (для проверки)
 * Все команды - для операторов (как ванильные /tp и /locate).
 */
public final class ProstorCommands {

	private static final int SEARCH_RADIUS = 6400;

	private static final SuggestionProvider<ServerCommandSource> BIOMES = (context, builder) ->
			CommandSource.suggestMatching(ProstorBiomes.ALL.stream().map(key -> key.getValue().getPath()), builder);

	private ProstorCommands() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("prostor")
				.requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
				.then(CommandManager.literal("tp")
						.then(CommandManager.argument("biome", StringArgumentType.word()).suggests(BIOMES)
								.executes(context -> run(context.getSource(), StringArgumentType.getString(context, "biome"), true))))
				.then(CommandManager.literal("locate")
						.then(CommandManager.argument("biome", StringArgumentType.word()).suggests(BIOMES)
								.executes(context -> run(context.getSource(), StringArgumentType.getString(context, "biome"), false))))
				.then(CommandManager.literal("locate_all").executes(context -> locateAll(context.getSource()))));
	}

	private static RegistryKey<Biome> parse(String name) {
		String path = name.toLowerCase(Locale.ROOT).replace("prostor:", "");
		for (RegistryKey<Biome> key : ProstorBiomes.ALL) {
			if (key.getValue().getPath().equals(path)) {
				return key;
			}
		}
		return null;
	}

	private static Text biomeName(RegistryKey<Biome> key) {
		return Text.translatable("biome." + key.getValue().getNamespace() + "." + key.getValue().getPath());
	}

	private static int run(ServerCommandSource source, String name, boolean teleport) {
		RegistryKey<Biome> key = parse(name);
		if (key == null) {
			source.sendError(Text.translatable("command.prostor.unknown_biome", name));
			return 0;
		}
		ServerWorld world = source.getWorld();
		if (world.getRegistryKey() != World.OVERWORLD) {
			source.sendError(Text.translatable("command.prostor.overworld_only"));
			return 0;
		}
		BlockPos from = BlockPos.ofFloored(source.getPosition());
		Viewpoint view = find(world, key, from);
		if (view == null) {
			source.sendError(Text.translatable("command.prostor.not_found", biomeName(key), SEARCH_RADIUS));
			return 0;
		}
		int distance = (int) Math.hypot(view.x - from.getX(), view.z - from.getZ());
		ServerPlayerEntity player = source.getPlayer();
		if (teleport && player != null) {
			player.teleport(world, view.x + 0.5, view.y, view.z + 0.5, Set.of(), view.yaw, view.pitch, true);
			source.sendFeedback(() -> Text.translatable("command.prostor.tp", biomeName(key), view.x, view.y, view.z, distance)
					.formatted(Formatting.GREEN), false);
		} else {
			source.sendFeedback(() -> Text.translatable("command.prostor.locate", biomeName(key), view.x, view.y, view.z, distance), false);
		}
		return 1;
	}

	private static int locateAll(ServerCommandSource source) {
		ServerWorld world = source.getServer().getOverworld();
		BlockPos from = BlockPos.ofFloored(source.getPosition());
		int found = 0;
		for (RegistryKey<Biome> key : ProstorBiomes.ALL) {
			Viewpoint view = find(world, key, from);
			if (view == null) {
				source.sendFeedback(() -> Text.translatable("command.prostor.not_found", biomeName(key), SEARCH_RADIUS), false);
				continue;
			}
			found++;
			int distance = (int) Math.hypot(view.x - from.getX(), view.z - from.getZ());
			source.sendFeedback(() -> Text.translatable("command.prostor.locate", biomeName(key), view.x, view.y, view.z, distance), false);
		}
		return found;
	}

	/** Точка обзора: координаты, куда встать, и направление взгляда. */
	public record Viewpoint(int x, int y, int z, float yaw, float pitch, Lakes.Lake lake) {
	}

	/** Ищет ближайший биом и в нём - удобную точку обзора (для Озёрного края - берег ближайшего озера). */
	public static Viewpoint find(ServerWorld world, RegistryKey<Biome> key, BlockPos from) {
		Pair<BlockPos, RegistryEntry<Biome>> pair = world.locateBiome(entry -> entry.matchesKey(key), from, SEARCH_RADIUS, 32, 64);
		if (pair == null) {
			return null;
		}
		BlockPos found = pair.getFirst();
		if (key.equals(ProstorBiomes.LAKE_DISTRICT)) {
			Viewpoint shore = lakeShore(world, found, from);
			if (shore != null) {
				return shore;
			}
		}
		world.getChunk(found.getX() >> 4, found.getZ() >> 4);
		int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, found.getX(), found.getZ());
		return new Viewpoint(found.getX(), y, found.getZ(), 0, 10, null);
	}

	private static Viewpoint lakeShore(ServerWorld world, BlockPos found, BlockPos from) {
		Lakes.Terrain terrain = new Lakes.Terrain(world.getSeed(), world.getChunkManager().getChunkGenerator(),
				world.getChunkManager().getNoiseConfig(), world);
		int cellX = Math.floorDiv(found.getX(), Lakes.CELL);
		int cellZ = Math.floorDiv(found.getZ(), Lakes.CELL);
		Lakes.Lake best = null;
		double bestDist = Double.MAX_VALUE;
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				Lakes.Lake lake = Lakes.lake(terrain, cellX + dx, cellZ + dz);
				if (lake == null) {
					continue;
				}
				double dist = Math.hypot(lake.centerX() - found.getX(), lake.centerZ() - found.getZ());
				if (dist < bestDist) {
					bestDist = dist;
					best = lake;
				}
			}
		}
		if (best == null) {
			return null;
		}
		// встаём на берег со стороны, откуда пришли, лицом к воде
		double theta = Math.atan2(from.getZ() - best.centerZ(), from.getX() - best.centerX()) - best.angle();
		double[] p = best.rimPoint(theta, Lakes.BANK + 3.0);
		int x = MathHelper.floor(p[0]);
		int z = MathHelper.floor(p[1]);
		world.getChunk(x >> 4, z >> 4);
		int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
		float yaw = (float) Math.toDegrees(Math.atan2(-(best.centerX() - x - 0.5), best.centerZ() - z - 0.5));
		return new Viewpoint(x, y, z, yaw, 12.0f, best);
	}
}
