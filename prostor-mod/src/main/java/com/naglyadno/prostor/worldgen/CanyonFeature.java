package com.naglyadno.prostor.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * Речные каньоны: глубокое ущелье с рекой на дне и слоистыми каменными стенами уступами.
 * Река стоит на уровне моря, поэтому там, где каньон выходит к реке или морю, вода сливается без перепадов,
 * а у края биома дно поднимается выше воды и каньон заканчивается сухим распадком.
 * Выполняется до растительности; меняет только свои столбцы.
 */
public class CanyonFeature extends Feature<DefaultFeatureConfig> {

	private static final BlockState AIR = Blocks.AIR.getDefaultState();
	private static final BlockState WATER = Blocks.WATER.getDefaultState();
	private static final int FLAGS = Block.NOTIFY_LISTENERS;
	/** Пласты стен снизу вверх (по высоте мира), толщина 2-5 блоков. */
	private static final BlockState[] STRATA = {
			Blocks.STONE.getDefaultState(), Blocks.ANDESITE.getDefaultState(), Blocks.STONE.getDefaultState(),
			Blocks.TUFF.getDefaultState(), Blocks.GRANITE.getDefaultState(), Blocks.STONE.getDefaultState(),
			Blocks.DRIPSTONE_BLOCK.getDefaultState(), Blocks.ANDESITE.getDefaultState(), Blocks.CALCITE.getDefaultState(),
			Blocks.STONE.getDefaultState(), Blocks.GRANITE.getDefaultState(), Blocks.TUFF.getDefaultState()};

	public CanyonFeature(Codec<DefaultFeatureConfig> codec) {
		super(codec);
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
		StructureWorldAccess world = context.getWorld();
		ChunkPos chunk = new ChunkPos(context.getOrigin());
		int bx = chunk.getStartX();
		int bz = chunk.getStartZ();
		Lakes.Terrain terrain = LakeFeature.terrain(world, context.getGenerator());
		double[] factor = BiomeFactor.chunk(terrain, ProstorBiomes.RIVER_CANYON, bx, bz);
		long seed = world.getSeed();
		int w = context.getGenerator().getSeaLevel() - 1;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		boolean changed = false;
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				double f = factor[dx * 16 + dz];
				if (f <= 0) {
					continue;
				}
				int x = bx + dx;
				int z = bz + dz;
				double u = Canyons.u(seed, x + 0.5, z + 0.5);
				if (u >= 1.25) {
					continue;
				}
				int top = LakeFeature.solidTop(world, x, z, pos);
				if (top <= w + 1 || !world.getBlockState(pos.set(x, top + 1, z)).getFluidState().isEmpty()) {
					continue;
				}
				// глубина каньона у края биома уменьшается: дно поднимается и река кончается
				double edgeFade = BiomeFactor.smooth(f);
				int floorY = w - 2;
				int dryFloor = (int) Math.round(top - (top - floorY) * edgeFade);
				int newTop;
				if (u < Canyons.FLOOR) {
					newTop = dryFloor;
				} else if (u < 1.0) {
					// стены: почти отвесные, с редкими полками через 7 блоков
					double k = (u - Canyons.FLOOR) / (1.0 - Canyons.FLOOR);
					double rise = Math.pow(k, 0.3);
					int y = (int) Math.round(dryFloor + (top - dryFloor) * rise);
					int ledge = 7;
					newTop = Math.min(top, dryFloor + ((y - dryFloor + ledge - 1) / ledge) * ledge);
				} else {
					newTop = top;
				}
				if (newTop < top) {
					for (int y = newTop + 1; y <= top; y++) {
						world.setBlockState(pos.set(x, y, z), AIR, FLAGS);
					}
					changed = true;
				}
				// слоистые стены: перекрашиваем камень от реки до верха
				for (int y = w - 3; y <= newTop; y++) {
					BlockState state = world.getBlockState(pos.set(x, y, z));
					if (state.isIn(BlockTags.BASE_STONE_OVERWORLD) || state.isIn(BlockTags.DIRT) && y < newTop - 3) {
						world.setBlockState(pos, stratum(seed, y), FLAGS);
					}
				}
				if (newTop < w) {
					// река на дне
					for (int y = newTop + 1; y <= w; y++) {
						world.setBlockState(pos.set(x, y, z), WATER, FLAGS);
					}
					double bed = Lakes.hash(seed, x, z, 41);
					world.setBlockState(pos.set(x, newTop, z), bed < 0.6 ? Blocks.GRAVEL.getDefaultState()
							: Blocks.SAND.getDefaultState(), FLAGS);
					seal(world, x, z, newTop - 2, newTop - 1, pos);
				} else if (newTop < top) {
					// сухое дно и уступы: земля с травой на ровных местах, на дне - галька
					BlockState surface = world.getBlockState(pos.set(x, newTop, z));
					if (u < Canyons.FLOOR && newTop <= w + 2) {
						world.setBlockState(pos, Blocks.GRAVEL.getDefaultState(), FLAGS);
					} else if (!surface.isAir() && u > 0.92 && Lakes.hash(seed, x, z, 42) < 0.55) {
						world.setBlockState(pos, Blocks.GRASS_BLOCK.getDefaultState(), FLAGS);
						world.setBlockState(pos.set(x, newTop - 1, z), Blocks.DIRT.getDefaultState(), FLAGS);
					}
				}
				// стенки у воды: пустоты (пещеры) ниже уровня реки заделываем, чтобы река не утекала
				if (u < 1.25) {
					for (int y = w - 4; y <= Math.min(w, newTop); y++) {
						BlockState state = world.getBlockState(pos.set(x, y, z));
						if (state.isAir() || !state.getFluidState().isEmpty() && y <= newTop) {
							world.setBlockState(pos, stratum(seed, y), FLAGS);
						}
					}
				}
			}
		}
		return changed;
	}

	/** Пласт породы на высоте y: одинаков по всему миру, поэтому слои на стенах совпадают. */
	private static BlockState stratum(long seed, int y) {
		int band = Math.floorDiv(y, 3);
		int jitter = (int) (Lakes.hash(seed, band, 0, 43) * 3);
		return STRATA[Math.floorMod(band + jitter, STRATA.length)];
	}

	private static void seal(StructureWorldAccess world, int x, int z, int fromY, int toY, BlockPos.Mutable pos) {
		for (int y = fromY; y <= toY; y++) {
			BlockState state = world.getBlockState(pos.set(x, y, z));
			if (state.isAir() || !state.getFluidState().isEmpty()) {
				world.setBlockState(pos, Blocks.STONE.getDefaultState(), FLAGS);
			}
		}
	}
}
