package com.naglyadno.nedra.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** Светошляпка: кучка светящихся грибов. Растёт на любом блоке с твёрдым верхом - на мху, грязи и камне. */
public class GlowcapBlock extends PlantBlock {

	public static final MapCodec<GlowcapBlock> CODEC = createCodec(GlowcapBlock::new);
	private static final VoxelShape SHAPE = Block.createColumnShape(10.0, 0.0, 9.0);

	public GlowcapBlock(AbstractBlock.Settings settings) {
		super(settings);
	}

	@Override
	public MapCodec<GlowcapBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	@Override
	protected boolean canPlantOnTop(BlockState floor, BlockView world, BlockPos pos) {
		return floor.isSideSolidFullSquare(world, pos, Direction.UP);
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (random.nextInt(10) == 0) {
			world.addParticleClient(ParticleTypes.GLOW, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.5,
					pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.0, 0.01, 0.0);
		}
	}
}
