package com.naglyadno.prostor.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * Топкая земля болотистых низин: пятна мха, грязи, подзола и грубой земли вместо сплошной травы,
 * моховые кочки. Пятна по 3x3 блока, детерминированно по координатам. Меняет только свои столбцы.
 */
public class MarshGroundFeature extends Feature<DefaultFeatureConfig> {

	private static final int FLAGS = Block.NOTIFY_LISTENERS;

	public MarshGroundFeature(Codec<DefaultFeatureConfig> codec) {
		super(codec);
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
		StructureWorldAccess world = context.getWorld();
		ChunkPos chunk = new ChunkPos(context.getOrigin());
		long seed = world.getSeed();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		boolean changed = false;
		for (int x = chunk.getStartX(); x < chunk.getStartX() + 16; x++) {
			for (int z = chunk.getStartZ(); z < chunk.getStartZ() + 16; z++) {
				int top = LakeFeature.solidTop(world, x, z, pos);
				if (!world.getBiome(pos.set(x, top, z)).matchesKey(ProstorBiomes.MARSH)) {
					continue;
				}
				BlockState ground = world.getBlockState(pos);
				if (!ground.isOf(Blocks.GRASS_BLOCK) || !world.isAir(pos.set(x, top + 1, z))) {
					continue;
				}
				double patch = Lakes.hash(seed, Math.floorDiv(x, 3), Math.floorDiv(z, 3), 61) * 0.8
						+ Lakes.hash(seed, x, z, 62) * 0.2;
				BlockState cover = patch < 0.16 ? Blocks.MUD.getDefaultState()
						: patch < 0.34 ? Blocks.MOSS_BLOCK.getDefaultState()
						: patch < 0.44 ? Blocks.PODZOL.getDefaultState()
						: patch < 0.50 ? Blocks.COARSE_DIRT.getDefaultState() : null;
				if (cover != null) {
					world.setBlockState(pos.set(x, top, z), cover, FLAGS);
					changed = true;
				}
				double hummock = Lakes.hash(seed, x, z, 63);
				if (hummock < 0.035) {
					// моховая кочка
					world.setBlockState(pos.set(x, top + 1, z), Blocks.MOSS_BLOCK.getDefaultState(), FLAGS);
					if (hummock < 0.015) {
						world.setBlockState(pos.set(x, top + 2, z), Blocks.MOSS_CARPET.getDefaultState(), FLAGS);
					}
					changed = true;
				} else if (cover != null && cover.isOf(Blocks.MOSS_BLOCK) && hummock < 0.25) {
					world.setBlockState(pos.set(x, top + 1, z), Blocks.MOSS_CARPET.getDefaultState(), FLAGS);
				}
			}
		}
		return changed;
	}
}
