package com.naglyadno.prostor.gametest;

import com.naglyadno.prostor.block.ModBlocks;
import com.naglyadno.prostor.worldgen.Lakes;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Замеры одного озера по настоящим блокам мира: ищет висящую и вытекающую воду и считает растения. */
final class LakeCheck {

	private static final Logger LOGGER = LoggerFactory.getLogger("prostor-gametest");

	private LakeCheck() {
	}

	static void report(ServerWorld world, Lakes.Lake lake) {
		int reach = lake.reach() + 2;
		int w = lake.waterY();
		int water = 0, floating = 0, leaks = 0, wrongLevel = 0, reeds = 0, lily = 0, seagrass = 0, cane = 0,
				firefly = 0, logs = 0, columns = 0;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		BlockPos.Mutable n = new BlockPos.Mutable();
		for (int x = lake.centerX() - reach; x <= lake.centerX() + reach; x++) {
			for (int z = lake.centerZ() - reach; z <= lake.centerZ() + reach; z++) {
				double q = lake.q(x + 0.5, z + 0.5);
				boolean inLake = q < 1.0 && lake.islandQ(x + 0.5, z + 0.5) >= 1.0;
				if (inLake) {
					columns++;
					BlockState surface = world.getBlockState(pos.set(x, w, z));
					BlockState above = world.getBlockState(pos.set(x, w + 1, z));
					boolean waterAtLevel = surface.getFluidState().isIn(net.minecraft.registry.tag.FluidTags.WATER);
					if (!waterAtLevel || above.getFluidState().isIn(net.minecraft.registry.tag.FluidTags.WATER)) {
						wrongLevel++;
					}
				}
				for (int y = w - lake.maxDepth() - 3; y <= w + 12; y++) {
					BlockState state = world.getBlockState(pos.set(x, y, z));
					if (state.isOf(Blocks.WATER)) {
						water++;
						BlockState below = world.getBlockState(n.set(x, y - 1, z));
						if (below.isAir()) {
							floating++;
						}
						for (Direction d : Direction.Type.HORIZONTAL) {
							if (world.getBlockState(n.set(x + d.getOffsetX(), y, z + d.getOffsetZ())).isAir()) {
								leaks++;
								break;
							}
						}
					} else if (state.isOf(ModBlocks.REEDS)) {
						reeds++;
					} else if (state.isOf(Blocks.LILY_PAD)) {
						lily++;
					} else if (state.isOf(Blocks.SEAGRASS)) {
						seagrass++;
					} else if (state.isOf(Blocks.SUGAR_CANE)) {
						cane++;
					} else if (state.isOf(Blocks.FIREFLY_BUSH)) {
						firefly++;
					} else if (state.isIn(BlockTags.LOGS)) {
						logs++;
					}
				}
			}
		}
		LOGGER.info("STATS| lake center {} {} radius {} stretch {} waterY {} maxDepth {} island {}", lake.centerX(),
				lake.centerZ(), lake.radius(), String.format(java.util.Locale.ROOT, "%.2f", lake.stretch()), w,
				lake.maxDepth(), lake.hasIsland());
		LOGGER.info("STATS| lake columns {} wrongWaterLevel {} waterBlocks {} floatingWater {} leakingWater {}", columns,
				wrongLevel, water, floating, leaks);
		LOGGER.info("STATS| lake plants: reeds {} lilyPads {} seagrass {} sugarCane {} fireflyBush {} logsNearby {}",
				reeds / 1, lily, seagrass, cane, firefly, logs);
	}

	/** Сколько клеток вокруг получили озеро (без чтения блоков - только расчёт). */
	static void reportArea(ServerWorld world, int x, int z) {
		Lakes.Terrain terrain = new Lakes.Terrain(world.getSeed(), world.getChunkManager().getChunkGenerator(),
				world.getChunkManager().getNoiseConfig(), world);
		int cx = Math.floorDiv(x, Lakes.LAKE_DISTRICT.cell());
		int cz = Math.floorDiv(z, Lakes.LAKE_DISTRICT.cell());
		int cells = 0, lakes = 0, islands = 0, radiusSum = 0;
		for (int dx = -5; dx <= 5; dx++) {
			for (int dz = -5; dz <= 5; dz++) {
				cells++;
				Lakes.Lake lake = Lakes.lake(Lakes.LAKE_DISTRICT, terrain, cx + dx, cz + dz);
				if (lake != null) {
					lakes++;
					radiusSum += lake.radius();
					if (lake.hasIsland()) {
						islands++;
					}
				}
			}
		}
		LOGGER.info("STATS| lakes around: {} of {} cells ({}x{} blocks), islands {}, mean radius {}", lakes, cells,
				11 * Lakes.LAKE_DISTRICT.cell(), 11 * Lakes.LAKE_DISTRICT.cell(), islands, lakes == 0 ? 0 : radiusSum / lakes);
	}

	/**
	 * Вода в квадрате вокруг точки: родники (источники), висящие над воздухом, и источники с воздухом сбоку
	 * (вода, которая потечёт при первом обновлении). Текущую воду водопадов не считаем.
	 */
	static void area(ServerWorld world, String name, BlockPos center, int radius) {
		int sources = 0, floating = 0, leaks = 0, flowing = 0;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		BlockPos.Mutable n = new BlockPos.Mutable();
		for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
			for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
				int top = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, x, z);
				for (int y = top - 12; y < top; y++) {
					BlockState state = world.getBlockState(pos.set(x, y, z));
					if (!state.isOf(Blocks.WATER)) {
						continue;
					}
					if (!state.getFluidState().isStill()) {
						flowing++;
						continue;
					}
					sources++;
					if (world.getBlockState(n.set(x, y - 1, z)).isAir()) {
						floating++;
					}
					for (Direction d : Direction.Type.HORIZONTAL) {
						if (world.getBlockState(n.set(x + d.getOffsetX(), y, z + d.getOffsetZ())).isAir()) {
							leaks++;
							break;
						}
					}
				}
			}
		}
		LOGGER.info("STATS| {} water: sources {} floatingSources {} sourcesNextToAir {} flowingWater {}", name, sources,
				floating, leaks, flowing);
	}
}
