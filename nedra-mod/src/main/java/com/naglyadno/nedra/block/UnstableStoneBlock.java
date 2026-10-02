package com.naglyadno.nedra.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Нестабильный камень. Если под ним пустота - с него постоянно сыплется каменная пыль:
 * так внимательный шахтёр может заметить опасный свод заранее, ещё до треска и обвала.
 */
public class UnstableStoneBlock extends Block {

	public UnstableStoneBlock(Settings settings) {
		super(settings);
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (random.nextInt(4) != 0) {
			return;
		}
		BlockPos below = pos.down();
		if (!world.getBlockState(below).isAir()) {
			return;
		}
		double x = pos.getX() + random.nextDouble();
		double y = pos.getY() - 0.05;
		double z = pos.getZ() + random.nextDouble();
		world.addParticleClient(new BlockStateParticleEffect(ParticleTypes.FALLING_DUST, state), x, y, z, 0.0, 0.0, 0.0);
	}
}
