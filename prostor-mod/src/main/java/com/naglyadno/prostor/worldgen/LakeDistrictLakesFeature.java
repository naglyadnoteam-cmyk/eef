package com.naglyadno.prostor.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

import java.util.List;

/**
 * Озёра Озёрного края. Выполняется до растительности, поэтому деревья, трава и цветы потом сами
 * вырастают по новым берегам. Меняет только блоки своего чанка.
 */
public class LakeDistrictLakesFeature extends Feature<DefaultFeatureConfig> {

	private static final BlockState AIR = Blocks.AIR.getDefaultState();
	private static final BlockState WATER = Blocks.WATER.getDefaultState();
	private static final BlockState DIRT = Blocks.DIRT.getDefaultState();
	private static final BlockState GRASS = Blocks.GRASS_BLOCK.getDefaultState();
	private static final BlockState SAND = Blocks.SAND.getDefaultState();
	private static final BlockState GRAVEL = Blocks.GRAVEL.getDefaultState();
	private static final BlockState CLAY = Blocks.CLAY.getDefaultState();
	private static final int FLAGS = Block.NOTIFY_LISTENERS;

	public LakeDistrictLakesFeature(Codec<DefaultFeatureConfig> codec) {
		super(codec);
	}

	public static Lakes.Terrain terrain(StructureWorldAccess world, net.minecraft.world.gen.chunk.ChunkGenerator generator) {
		return new Lakes.Terrain(world.getSeed(), generator, world.toServerWorld().getChunkManager().getNoiseConfig(), world);
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
		StructureWorldAccess world = context.getWorld();
		ChunkPos chunk = new ChunkPos(context.getOrigin());
		int bx = chunk.getStartX();
		int bz = chunk.getStartZ();
		List<Lakes.Lake> lakes = Lakes.near(terrain(world, context.getGenerator()), bx, bz, bx + 15, bz + 15);
		if (lakes.isEmpty()) {
			return false;
		}
		long seed = world.getSeed();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (Lakes.Lake lake : lakes) {
			for (int x = bx; x < bx + 16; x++) {
				for (int z = bz; z < bz + 16; z++) {
					column(world, lake, seed, x, z, pos);
				}
			}
		}
		return true;
	}

	/** Верхний твёрдый (не воздух и не жидкость) блок столбца. */
	static int solidTop(StructureWorldAccess world, int x, int z, BlockPos.Mutable pos) {
		int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
		int bottom = world.getBottomY();
		while (y > bottom) {
			BlockState state = world.getBlockState(pos.set(x, y, z));
			if (!state.isAir() && state.getFluidState().isEmpty()) {
				return y;
			}
			y--;
		}
		return bottom;
	}

	private static void column(StructureWorldAccess world, Lakes.Lake lake, long seed, int x, int z, BlockPos.Mutable pos) {
		double q = lake.q(x + 0.5, z + 0.5);
		double t = Lakes.bank(lake, q);
		if (t >= 1.0) {
			return;
		}
		int w = lake.waterY();
		int top = solidTop(world, x, z, pos);
		double iq = lake.islandQ(x + 0.5, z + 0.5);

		if (q < 1.0 && iq < 1.0) {
			// остров: суша на 1-2 блока над водой
			int islandTop = w + (iq < 0.45 ? 2 : 1);
			fill(world, x, z, top + 1, islandTop - 1, DIRT, pos);
			clear(world, x, z, islandTop + 1, Math.max(top, islandTop), pos);
			world.setBlockState(pos.set(x, islandTop, z), GRASS, FLAGS);
			return;
		}

		if (q < 1.0) {
			int floor = w - Lakes.depth(lake, q, iq);
			clear(world, x, z, w + 1, top, pos);
			fill(world, x, z, top + 1, floor - 1, DIRT, pos);
			for (int y = floor + 1; y <= w; y++) {
				world.setBlockState(pos.set(x, y, z), WATER, FLAGS);
			}
			world.setBlockState(pos.set(x, floor, z), bedMaterial(seed, x, z, q), FLAGS);
			// дно не должно проваливаться в пещеру под озером
			seal(world, x, z, floor - 3, floor - 1, pos);
			return;
		}

		// берег: у самой воды земля вровень с водой (там растут тростник и камыш), дальше плавный подъём
		int cap = w + (int) Math.floor(t * 4.5);
		if (top > cap) {
			clear(world, x, z, cap + 1, top, pos);
			BlockState surface = world.getBlockState(pos.set(x, cap, z));
			if (!surface.isOf(Blocks.STONE) || t < 0.5) {
				world.setBlockState(pos, GRASS, FLAGS);
				world.setBlockState(pos.set(x, cap - 1, z), DIRT, FLAGS);
			}
			top = cap;
		} else if (top < w) {
			// ложбинка у кромки: насыпаем вал, чтобы вода не стояла стеной
			fill(world, x, z, top + 1, w - 1, DIRT, pos);
			world.setBlockState(pos.set(x, w, z), GRASS, FLAGS);
			top = w;
		}
		// песок и галька у самой воды - пятнами
		if (t < 0.35 && top <= w + 1 && world.getBlockState(pos.set(x, top + 1, z)).isAir()) {
			double patch = Lakes.hash(seed, Math.floorDiv(x, 3), Math.floorDiv(z, 3), 11);
			if (patch < 0.3) {
				world.setBlockState(pos.set(x, top, z), patch < 0.18 ? SAND : GRAVEL, FLAGS);
			}
		}
		if (t < 0.6) {
			seal(world, x, z, w - lake.maxDepth() - 1, w, pos);
		}
	}

	/** Материал дна: у берега песок и галька, на глубине глина и земля. Пятнами по 3x3. */
	private static BlockState bedMaterial(long seed, int x, int z, double q) {
		double patch = Lakes.hash(seed, Math.floorDiv(x, 3), Math.floorDiv(z, 3), 5);
		double grain = Lakes.hash(seed, x, z, 6);
		double h = patch * 0.8 + grain * 0.2;
		if (q > 0.8) {
			return h < 0.45 ? SAND : h < 0.75 ? GRAVEL : DIRT;
		}
		if (q > 0.45) {
			return h < 0.4 ? DIRT : h < 0.7 ? GRAVEL : CLAY;
		}
		return h < 0.45 ? CLAY : h < 0.7 ? GRAVEL : DIRT;
	}

	private static void clear(StructureWorldAccess world, int x, int z, int fromY, int toY, BlockPos.Mutable pos) {
		for (int y = fromY; y <= toY; y++) {
			world.setBlockState(pos.set(x, y, z), AIR, FLAGS);
		}
	}

	private static void fill(StructureWorldAccess world, int x, int z, int fromY, int toY, BlockState state,
			BlockPos.Mutable pos) {
		for (int y = fromY; y <= toY; y++) {
			world.setBlockState(pos.set(x, y, z), state, FLAGS);
		}
	}

	/** Заделывает пустоты и подземную воду под дном и в берегу землёй, чтобы озеро не утекало в пещеры. */
	private static void seal(StructureWorldAccess world, int x, int z, int fromY, int toY, BlockPos.Mutable pos) {
		for (int y = fromY; y <= toY; y++) {
			BlockState state = world.getBlockState(pos.set(x, y, z));
			if (state.isAir() || !state.getFluidState().isEmpty()) {
				world.setBlockState(pos, DIRT, FLAGS);
			}
		}
	}
}
