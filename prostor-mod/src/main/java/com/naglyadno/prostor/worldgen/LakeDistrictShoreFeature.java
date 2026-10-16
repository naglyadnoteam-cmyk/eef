package com.naglyadno.prostor.worldgen;

import com.mojang.serialization.Codec;
import com.naglyadno.prostor.block.ModBlocks;
import com.naglyadno.prostor.block.ReedsBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SugarCaneBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

import java.util.List;

/**
 * Жизнь у воды: камыш на мелководье и у кромки, кувшинки, водоросли, тростник и светлячковые кусты
 * на берегу. Последний шаг растительности, ставит блоки только в своём чанке.
 */
public class LakeDistrictShoreFeature extends Feature<DefaultFeatureConfig> {

	private static final int FLAGS = Block.NOTIFY_LISTENERS;

	public LakeDistrictShoreFeature(Codec<DefaultFeatureConfig> codec) {
		super(codec);
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
		StructureWorldAccess world = context.getWorld();
		ChunkPos chunk = new ChunkPos(context.getOrigin());
		int bx = chunk.getStartX();
		int bz = chunk.getStartZ();
		List<Lakes.Lake> lakes = Lakes.near(LakeDistrictLakesFeature.terrain(world, context.getGenerator()), bx, bz, bx + 15, bz + 15);
		long seed = world.getSeed();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (Lakes.Lake lake : lakes) {
			for (int x = bx; x < bx + 16; x++) {
				for (int z = bz; z < bz + 16; z++) {
					double q = lake.q(x + 0.5, z + 0.5);
					double iq = lake.islandQ(x + 0.5, z + 0.5);
					double h = Lakes.hash(seed, x, z, 21);
					if (q < 1.0 && iq >= 1.0) {
						water(world, lake, x, z, q, iq, h, pos);
					} else if (Lakes.bank(lake, q) < 0.8 || iq < 1.0) {
						shore(world, x, z, h, Lakes.hash(seed, x, z, 22), pos);
					}
				}
			}
		}
		return !lakes.isEmpty();
	}

	private static void water(StructureWorldAccess world, Lakes.Lake lake, int x, int z, double q, double iq, double h,
			BlockPos.Mutable pos) {
		int w = lake.waterY();
		if (!world.getBlockState(pos.set(x, w, z)).isOf(Blocks.WATER) || !world.isAir(pos.set(x, w + 1, z))) {
			return;
		}
		int depth = Lakes.depth(lake, q, iq);
		int floor = w - depth;
		if (depth == 1) {
			// мелководье: заросли камыша, чаще у самой кромки
			double chance = q > 0.9 ? 0.55 : 0.3;
			if (h < chance && world.isAir(pos.set(x, w + 2, z))
					&& ReedsBlock.isSoil(world.getBlockState(pos.set(x, floor, z)))) {
				placeReeds(world, x, w, z, true, pos);
			}
			return;
		}
		if (h < 0.06 && q < 0.85) {
			world.setBlockState(pos.set(x, w + 1, z), Blocks.LILY_PAD.getDefaultState(), FLAGS);
		} else if (h > 0.78 && floor + 2 <= w) {
			BlockState bed = world.getBlockState(pos.set(x, floor, z));
			if (bed.isSideSolidFullSquare(world, pos, Direction.UP)) {
				world.setBlockState(pos.set(x, floor + 1, z), Blocks.SEAGRASS.getDefaultState(), FLAGS);
			}
		}
	}

	private static void shore(StructureWorldAccess world, int x, int z, double h, double h2, BlockPos.Mutable pos) {
		int top = LakeDistrictLakesFeature.solidTop(world, x, z, pos);
		BlockState ground = world.getBlockState(pos.set(x, top, z));
		BlockState above = world.getBlockState(pos.set(x, top + 1, z));
		if (!(above.isAir() || above.isReplaceable() && above.getFluidState().isEmpty())) {
			return;
		}
		if (!nextToWater(world, x, top, z, pos)) {
			return;
		}
		boolean soil = ground.isIn(BlockTags.DIRT) || ground.isIn(BlockTags.SAND);
		if (!soil) {
			return;
		}
		if (h < 0.24 && world.isAir(pos.set(x, top + 2, z)) && ReedsBlock.isSoil(ground)) {
			placeReeds(world, x, top + 1, z, false, pos);
		} else if (h < 0.34) {
			int height = 2 + (int) (h2 * 2.0);
			for (int i = 1; i <= height; i++) {
				if (!world.isAir(pos.set(x, top + i, z)) && i > 1) {
					break;
				}
				world.setBlockState(pos.set(x, top + i, z), Blocks.SUGAR_CANE.getDefaultState().with(SugarCaneBlock.AGE, 0), FLAGS);
			}
		} else if (h < 0.40 && ground.isIn(BlockTags.DIRT)) {
			world.setBlockState(pos.set(x, top + 1, z), Blocks.FIREFLY_BUSH.getDefaultState(), FLAGS);
		}
	}

	private static boolean nextToWater(StructureWorldAccess world, int x, int y, int z, BlockPos.Mutable pos) {
		for (Direction direction : Direction.Type.HORIZONTAL) {
			if (world.getBlockState(pos.set(x + direction.getOffsetX(), y, z + direction.getOffsetZ())).isOf(Blocks.WATER)) {
				return true;
			}
		}
		return false;
	}

	private static void placeReeds(StructureWorldAccess world, int x, int y, int z, boolean inWater, BlockPos.Mutable pos) {
		BlockState lower = ModBlocks.REEDS.getDefaultState().with(TallPlantBlock.HALF, DoubleBlockHalf.LOWER)
				.with(ReedsBlock.WATERLOGGED, inWater);
		BlockState upper = ModBlocks.REEDS.getDefaultState().with(TallPlantBlock.HALF, DoubleBlockHalf.UPPER)
				.with(ReedsBlock.WATERLOGGED, false);
		world.setBlockState(pos.set(x, y, z), lower, FLAGS);
		world.setBlockState(pos.set(x, y + 1, z), upper, FLAGS);
	}
}
