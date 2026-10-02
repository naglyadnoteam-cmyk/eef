package com.naglyadno.nedra.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

/** Глубинный мох: растёт на полу пещер, светится и выпускает споры. Сырьё для таблеток от давления. */
public class DeepmossBlock extends Block {

	public DeepmossBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
		BlockPos below = pos.down();
		return world.getBlockState(below).isSideSolidFullSquare(world, below, Direction.UP);
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (random.nextInt(6) != 0 || !world.getBlockState(pos.up()).isAir()) {
			return;
		}
		world.addParticleClient(ParticleTypes.SPORE_BLOSSOM_AIR,
				pos.getX() + random.nextDouble(), pos.getY() + 1.05, pos.getZ() + random.nextDouble(),
				0.0, 0.0, 0.0);
	}
}
