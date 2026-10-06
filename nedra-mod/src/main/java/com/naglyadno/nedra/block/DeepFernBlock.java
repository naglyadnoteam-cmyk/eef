package com.naglyadno.nedra.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/** Глубинный папоротник: растёт на мху, грязи и земле (тег minecraft:dirt), как обычные растения. */
public class DeepFernBlock extends PlantBlock {

	public static final MapCodec<DeepFernBlock> CODEC = createCodec(DeepFernBlock::new);
	private static final VoxelShape SHAPE = Block.createColumnShape(12.0, 0.0, 13.0);

	public DeepFernBlock(AbstractBlock.Settings settings) {
		super(settings);
	}

	@Override
	public MapCodec<DeepFernBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}
}
