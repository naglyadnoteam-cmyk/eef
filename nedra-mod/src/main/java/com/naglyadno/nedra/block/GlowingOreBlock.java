package com.naglyadno.nedra.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.ExperienceDroppingBlock;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.intprovider.IntProvider;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/** Люменитовая руда: светится и изредка роняет искорки с открытых граней. */
public class GlowingOreBlock extends ExperienceDroppingBlock {

	public static final MapCodec<GlowingOreBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			IntProvider.createValidatingCodec(0, 10).fieldOf("experience").forGetter(block -> block.experience),
			createSettingsCodec()
	).apply(instance, GlowingOreBlock::new));

	private final IntProvider experience;

	public GlowingOreBlock(IntProvider experience, Settings settings) {
		super(experience, settings);
		this.experience = experience;
	}

	@Override
	public MapCodec<GlowingOreBlock> getCodec() {
		return CODEC;
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (random.nextInt(5) != 0) {
			return;
		}
		Direction face = Direction.random(random);
		BlockPos neighbor = pos.offset(face);
		if (world.getBlockState(neighbor).isOpaqueFullCube()) {
			return;
		}
		double x = pos.getX() + 0.5 + face.getOffsetX() * 0.55 + (face.getOffsetX() == 0 ? random.nextDouble() - 0.5 : 0);
		double y = pos.getY() + 0.5 + face.getOffsetY() * 0.55 + (face.getOffsetY() == 0 ? random.nextDouble() - 0.5 : 0);
		double z = pos.getZ() + 0.5 + face.getOffsetZ() * 0.55 + (face.getOffsetZ() == 0 ? random.nextDouble() - 0.5 : 0);
		world.addParticleClient(ParticleTypes.GLOW, x, y, z, 0.0, 0.01, 0.0);
	}
}
