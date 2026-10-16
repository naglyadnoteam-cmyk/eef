package com.naglyadno.prostor.worldgen;

import com.mojang.serialization.Codec;
import com.naglyadno.prostor.block.ModBlocks;
import com.naglyadno.prostor.block.ReedsBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * Широкие реки с поймами. Ванильная река остаётся на месте, но вокруг неё:
 * <ul>
 * <li>низкие берега (до 2 блоков над водой) уходят под воду - русло становится шире, с мелководьем;</li>
 * <li>склоны до 12 блоков над водой опускаются в ровную пойму в 1 блок над рекой;</li>
 * <li>у края биома всё плавно возвращается к обычному рельефу (см. {@link BiomeFactor}).</li>
 * </ul>
 * Выполняется до растительности; меняет только свои столбцы.
 */
public class FloodplainFeature extends Feature<DefaultFeatureConfig> {

	private static final BlockState AIR = Blocks.AIR.getDefaultState();
	private static final BlockState WATER = Blocks.WATER.getDefaultState();
	private static final BlockState DIRT = Blocks.DIRT.getDefaultState();
	private static final BlockState GRASS = Blocks.GRASS_BLOCK.getDefaultState();
	private static final int FLAGS = Block.NOTIFY_LISTENERS;

	public FloodplainFeature(Codec<DefaultFeatureConfig> codec) {
		super(codec);
	}

	/** Высота поймы над уровнем воды для склона высотой h над водой. Непрерывна, поэтому без ступенек. */
	static double plain(int h) {
		if (h <= 1) {
			return h;
		}
		if (h <= 5) {
			return 1;
		}
		// от 5 до 12 блоков - подъём к естественному склону
		return 1 + (h - 5) * (11.0 / 7.0);
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
		StructureWorldAccess world = context.getWorld();
		ChunkPos chunk = new ChunkPos(context.getOrigin());
		int bx = chunk.getStartX();
		int bz = chunk.getStartZ();
		Lakes.Terrain terrain = LakeFeature.terrain(world, context.getGenerator());
		double[] factor = BiomeFactor.chunk(terrain, ProstorBiomes.FLOODPLAIN, bx, bz);
		int w = context.getGenerator().getSeaLevel() - 1;
		long seed = world.getSeed();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		boolean changed = false;
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				double f = BiomeFactor.smooth(factor[dx * 16 + dz]);
				if (f <= 0) {
					continue;
				}
				int x = bx + dx;
				int z = bz + dz;
				int top = LakeFeature.solidTop(world, x, z, pos);
				if (!world.getBlockState(pos.set(x, top + 1, z)).isAir()) {
					continue; // уже под водой (сама река) или что-то стоит
				}
				int h = top - w;
				if (h < 0 || h > 12) {
					continue;
				}
				double widen = Lakes.hash(seed, Math.floorDiv(x, 5), Math.floorDiv(z, 5), 31);
				if (h <= 2 && f > 0.75 && (h <= 1 || widen < 0.55)) {
					// низкий берег уходит под воду: у края - мелководье в 1 блок, глубже - 2
					int depth = h <= 0 && f > 0.9 ? 2 : 1;
					int floor = w - depth;
					for (int y = w + 1; y <= top; y++) {
						world.setBlockState(pos.set(x, y, z), AIR, FLAGS);
					}
					for (int y = floor + 1; y <= w; y++) {
						world.setBlockState(pos.set(x, y, z), WATER, FLAGS);
					}
					double bed = Lakes.hash(seed, x, z, 32);
					world.setBlockState(pos.set(x, floor, z), bed < 0.45 ? Blocks.SAND.getDefaultState()
							: bed < 0.75 ? Blocks.GRAVEL.getDefaultState() : Blocks.CLAY.getDefaultState(), FLAGS);
					seal(world, x, z, floor - 2, floor - 1, pos);
					if (depth == 1 && bed > 0.35 && world.isAir(pos.set(x, w + 2, z))) {
						// заросли камыша вдоль кромки
						world.setBlockState(pos.set(x, w, z), ModBlocks.REEDS.getDefaultState()
								.with(TallPlantBlock.HALF, DoubleBlockHalf.LOWER).with(ReedsBlock.WATERLOGGED, true), FLAGS);
						world.setBlockState(pos.set(x, w + 1, z), ModBlocks.REEDS.getDefaultState()
								.with(TallPlantBlock.HALF, DoubleBlockHalf.UPPER), FLAGS);
					}
					changed = true;
					continue;
				}
				int target = w + (int) Math.round(h - (h - plain(h)) * f);
				if (target < top) {
					for (int y = target + 1; y <= top; y++) {
						world.setBlockState(pos.set(x, y, z), AIR, FLAGS);
					}
					BlockState surface = world.getBlockState(pos.set(x, target, z));
					if (surface.isIn(BlockTags.DIRT) || surface.isIn(BlockTags.BASE_STONE_OVERWORLD) || surface.isOf(Blocks.GRAVEL)) {
						world.setBlockState(pos, GRASS, FLAGS);
						world.setBlockState(pos.set(x, target - 1, z), DIRT, FLAGS);
					}
					changed = true;
				}
			}
		}
		return changed;
	}

	private static void seal(StructureWorldAccess world, int x, int z, int fromY, int toY, BlockPos.Mutable pos) {
		for (int y = fromY; y <= toY; y++) {
			BlockState state = world.getBlockState(pos.set(x, y, z));
			if (state.isAir() || !state.getFluidState().isEmpty()) {
				world.setBlockState(pos, DIRT, FLAGS);
			}
		}
	}
}
