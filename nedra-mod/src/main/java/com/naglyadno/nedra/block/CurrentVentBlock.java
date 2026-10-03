package com.naglyadno.nedra.block;

import com.naglyadno.nedra.sound.ModSounds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Воздушный разлом: из открытого верха бьёт восходящий поток воздуха. Сам подброс игроков
 * считает {@link com.naglyadno.nedra.hazard.CurrentManager}, а здесь - только видимая струя и свист.
 */
public class CurrentVentBlock extends Block {

	public CurrentVentBlock(Settings settings) {
		super(settings);
	}

	public static boolean isActive(World world, BlockPos pos) {
		return world.getBlockState(pos.up()).isAir();
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (!isActive(world, pos)) {
			return;
		}
		// лёгкий пар над разломом и вихрь ветра повыше - струя читается как поток воздуха, а не дым
		if (random.nextBoolean()) {
			double x = pos.getX() + 0.35 + random.nextDouble() * 0.3;
			double z = pos.getZ() + 0.35 + random.nextDouble() * 0.3;
			world.addParticleClient(ParticleTypes.CLOUD, x, pos.getY() + 1.05, z,
					(random.nextDouble() - 0.5) * 0.01, 0.06 + random.nextDouble() * 0.04, (random.nextDouble() - 0.5) * 0.01);
		}
		if (random.nextInt(3) == 0) {
			world.addParticleClient(ParticleTypes.SMALL_GUST, pos.getX() + 0.5, pos.getY() + 1.3 + random.nextDouble() * 1.5,
					pos.getZ() + 0.5, 0.0, 0.0, 0.0);
		}
		if (random.nextInt(18) == 0) {
			world.playSoundClient(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, ModSounds.VENT_GUST,
					SoundCategory.BLOCKS, 0.4f, 0.8f + random.nextFloat() * 0.4f, true);
		}
	}
}
