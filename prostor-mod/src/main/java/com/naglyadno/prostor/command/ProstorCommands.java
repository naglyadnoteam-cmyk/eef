package com.naglyadno.prostor.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.datafixers.util.Pair;
import com.naglyadno.prostor.worldgen.Lakes;
import com.naglyadno.prostor.worldgen.ProstorBiomes;
import net.minecraft.block.BlockState;
import net.minecraft.command.CommandSource;
import net.minecraft.registry.tag.BlockTags;
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
		double preferred = Math.atan2(from.getZ() - best.centerZ(), from.getX() - best.centerX());
		return lakeViewpoint(world, best, preferred);
	}

	/**
	 * Лучшая точка на берегу озера: перебирает 16 мест вокруг воды и выбирает то, откуда озеро видно
	 * без деревьев и холмов на линии взгляда; при равенстве - ближе к направлению preferred (радианы,
	 * направление от центра озера, 0 - восток).
	 */
	public static Viewpoint lakeViewpoint(ServerWorld world, Lakes.Lake lake, double preferred) {
		return lakeViewpoint(world, lake, preferred, 1.62);
	}

	/** То же для заданной высоты глаз над землёй (у стоящего игрока 1.62). */
	public static Viewpoint lakeViewpoint(ServerWorld world, Lakes.Lake lake, double preferred, double eyeHeight) {
		Viewpoint best = null;
		double bestScore = Double.MAX_VALUE;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int i = 0; i < 16; i++) {
			double[] p = lake.rimPoint(Math.PI * 2 * i / 16, 6.0);
			int x = MathHelper.floor(p[0]);
			int z = MathHelper.floor(p[1]);
			world.getChunk(x >> 4, z >> 4);
			int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
			if (!world.getFluidState(pos.set(x, y - 1, z)).isEmpty()) {
				continue;
			}
			double score = obstruction(world, x + 0.5, y + eyeHeight, z + 0.5, lake.centerX() + 0.5, lake.waterY() + 0.5,
					lake.centerZ() + 0.5, pos) * 4.0;
			// не под кроной и не вплотную к стволу: иначе листва закроет вид
			score += canopy(world, x, y, z, pos) * 3.0;
			double dir = Math.atan2(z + 0.5 - lake.centerZ(), x + 0.5 - lake.centerX());
			score += Math.abs(MathHelper.wrapDegrees(Math.toDegrees(dir - preferred))) / 120.0;
			if (score < bestScore) {
				bestScore = score;
				float yaw = (float) Math.toDegrees(Math.atan2(-(lake.centerX() - x - 0.5), lake.centerZ() - z - 0.5));
				best = new Viewpoint(x, y, z, yaw, 12.0f, lake);
			}
		}
		return best;
	}

	/** Сколько листвы и стволов рядом с головой и над ней (квадрат 3x3, высота до 6 блоков). */
	private static int canopy(ServerWorld world, int x, int y, int z, BlockPos.Mutable pos) {
		int count = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dy = 0; dy <= 6; dy++) {
					BlockState state = world.getBlockState(pos.set(x + dx, y + dy, z + dz));
					if (state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.LOGS)) {
						count++;
					}
				}
			}
		}
		return count;
	}

	/** Сколько твёрдых блоков и листвы на отрезке от глаз до точки (без повторов). */
	private static int obstruction(ServerWorld world, double x0, double y0, double z0, double x1, double y1, double z1,
			BlockPos.Mutable pos) {
		double length = Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0) + (z1 - z0) * (z1 - z0));
		int steps = (int) Math.ceil(length * 2);
		int count = 0;
		long last = Long.MIN_VALUE;
		for (int i = 1; i < steps; i++) {
			double k = (double) i / steps;
			pos.set(MathHelper.floor(x0 + (x1 - x0) * k), MathHelper.floor(y0 + (y1 - y0) * k), MathHelper.floor(z0 + (z1 - z0) * k));
			long key = pos.asLong();
			if (key == last) {
				continue;
			}
			last = key;
			BlockState state = world.getBlockState(pos);
			if (state.isIn(BlockTags.LEAVES) || !state.getCollisionShape(world, pos).isEmpty()) {
				count++;
			}
		}
		return count;
	}
}
