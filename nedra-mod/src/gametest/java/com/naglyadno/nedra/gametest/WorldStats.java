package com.naglyadno.nedra.gametest;

import com.naglyadno.nedra.block.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/** Сводка по сгенерированным вокруг спавна чанкам: рельеф, жидкости и полости по ярусам, руды, биомы. */
final class WorldStats {

	private static final Logger LOGGER = LoggerFactory.getLogger("nedra-gametest");
	private static final int[] BANDS = {-352, -338, -272, -176, -80, -64, 0, 63, 320};
	private static final int RADIUS = 5;

	private static final Map<Block, String> ORE_NAMES = new HashMap<>();

	private WorldStats() {
	}

	/** Короткое имя руды (deepslate_ и _ore отрезаны, ванильная и глубинная руда считаются вместе) или null. */
	private static String oreName(Block block) {
		Identifier id = Registries.BLOCK.getId(block);
		String path = id.getPath();
		if (!path.endsWith("_ore") || path.equals("nether_gold_ore") || path.equals("nether_quartz_ore")) {
			return null;
		}
		String name = path.replaceFirst("^deepslate_", "").replaceFirst("_ore$", "");
		return id.getNamespace().equals("minecraft") ? name : id.getNamespace() + ":" + name;
	}

	/** Возвращает самую просторную точку в глубинной пещере (x, y, z) или null. */
	static int[] report(MinecraftServer server) {
		ServerWorld world = server.getOverworld();
		BlockPos spawn = server.getPlayerManager().getPlayerList().get(0).getBlockPos();
		int scx = spawn.getX() >> 4;
		int scz = spawn.getZ() >> 4;
		int bands = BANDS.length - 1;
		long[] air = new long[bands], lava = new long[bands], water = new long[bands], total = new long[bands];
		Map<String, Integer> ores = new TreeMap<>();
		Map<String, Integer> exposed = new TreeMap<>();
		Map<String, Integer> biomes = new TreeMap<>();
		int minSurface = Integer.MAX_VALUE, maxSurface = Integer.MIN_VALUE;
		long surfaceSum = 0;
		int columns = 0, chunks = 0;
		int[] cave = null;
		int bestOpen = 0;

		for (int cx = scx - RADIUS; cx <= scx + RADIUS; cx++) {
			for (int cz = scz - RADIUS; cz <= scz + RADIUS; cz++) {
				if (!world.getChunkManager().isChunkLoaded(cx, cz)) {
					continue;
				}
				WorldChunk chunk = world.getChunk(cx, cz);
				chunks++;
				for (int lx = 0; lx < 16; lx += 4) {
					for (int lz = 0; lz < 16; lz += 4) {
						int h = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, lx, lz);
						minSurface = Math.min(minSurface, h);
						maxSurface = Math.max(maxSurface, h);
						surfaceSum += h;
						columns++;
					}
				}
				ChunkSection[] sections = chunk.getSectionArray();
				for (int i = 0; i < sections.length; i++) {
					ChunkSection section = sections[i];
					int baseY = chunk.sectionIndexToCoord(i) << 4;
					for (int ly = 0; ly < 16; ly++) {
						int y = baseY + ly;
						int band = band(y);
						if (band < 0) {
							continue;
						}
						for (int lz = 0; lz < 16; lz++) {
							for (int lx = 0; lx < 16; lx++) {
								BlockState state = section.getBlockState(lx, ly, lz);
								total[band]++;
								if (state.isAir()) {
									air[band]++;
									if (y > -230 && y < -110 && lx >= 4 && lx < 12 && lz >= 4 && lz < 12 && ly >= 4 && ly < 12
											&& section.getBlockState(lx, ly + 1, lz).isAir()
											&& section.getBlockState(lx, ly - 1, lz).isSolid()) {
										int open = openness(section, lx, ly, lz);
										if (open > bestOpen) {
											bestOpen = open;
											cave = new int[]{(cx << 4) + lx, y, (cz << 4) + lz};
										}
									}
								} else if (state.isOf(Blocks.LAVA)) {
									lava[band]++;
								} else if (state.isOf(Blocks.WATER)) {
									water[band]++;
								} else if (ORE_NAMES.computeIfAbsent(state.getBlock(), WorldStats::oreName) != null) {
									// руда по ярусам: всего и сколько блоков видно из пещеры (касаются воздуха или воды)
									String key = BANDS[band] + " " + ORE_NAMES.get(state.getBlock());
									ores.merge(key, 1, Integer::sum);
									BlockPos pos = new BlockPos((cx << 4) + lx, y, (cz << 4) + lz);
									for (Direction direction : Direction.values()) {
										BlockState next = world.getBlockState(pos.offset(direction));
										if (next.isAir() || next.isOf(Blocks.WATER)) {
											exposed.merge(key, 1, Integer::sum);
											break;
										}
									}
								}
							}
						}
					}
				}
				for (int y = -350; y < 0; y += 8) {
					RegistryEntry<Biome> biome = world.getBiome(new BlockPos((cx << 4) + 8, y, (cz << 4) + 8));
					String name = biome.getKey().map(k -> k.getValue().getPath()).orElse("?");
					biomes.merge(BANDS[Math.max(0, band(y))] + ":" + name, 1, Integer::sum);
				}
			}
		}

		LOGGER.info("STATS| chunks={} surface min={} max={} avg={}", chunks, minSurface, maxSurface,
				columns == 0 ? 0 : surfaceSum / columns);
		for (int b = bands - 1; b >= 0; b--) {
			if (total[b] == 0) {
				continue;
			}
			LOGGER.info(String.format("STATS| Y %4d..%4d  air %5.1f%%  lava %5.2f%%  water %5.2f%%",
					BANDS[b], BANDS[b + 1], 100.0 * air[b] / total[b], 100.0 * lava[b] / total[b], 100.0 * water[b] / total[b]));
		}
		LOGGER.info("STATS| ores per band (lower Y): total / visible from caves, per chunk");
		final int chunkCount = Math.max(1, chunks);
		ores.forEach((k, v) -> LOGGER.info(String.format("STATS| ore %-28s %7d / %6d   %7.1f / %6.1f", k, v,
				exposed.getOrDefault(k, 0), v / (double) chunkCount, exposed.getOrDefault(k, 0) / (double) chunkCount)));
		biomes.forEach((k, v) -> LOGGER.info("STATS| biome {} = {}", k, v));
		LOGGER.info("STATS| deep cave spot: {}", cave == null ? "none" : cave[0] + " " + cave[1] + " " + cave[2]);
		return cave;
	}

	/** Сколько воздуха вокруг точки (куб 9x9x9 внутри секции) - для снимка выбираем самую просторную пещеру. */
	private static int openness(ChunkSection section, int x, int y, int z) {
		int open = 0;
		for (int dy = -4; dy <= 3; dy++) {
			for (int dz = -4; dz <= 3; dz++) {
				for (int dx = -4; dx <= 3; dx++) {
					if (section.getBlockState(x + dx, y + dy, z + dz).isAir()) {
						open++;
					}
				}
			}
		}
		return open;
	}

	private static int band(int y) {
		for (int b = 0; b < BANDS.length - 1; b++) {
			if (y >= BANDS[b] && y < BANDS[b + 1]) {
				return b;
			}
		}
		return -1;
	}
}
