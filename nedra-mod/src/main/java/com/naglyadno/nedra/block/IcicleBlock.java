package com.naglyadno.nedra.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.tick.ScheduledTickView;

/**
 * Сосулька: тонкий ледяной шип, свисающий со свода Замёрзших пещер. Держится за твёрдый низ блока сверху
 * (или за другую сосульку); без опоры осыпается. С кончика изредка срывается капля талой воды.
 */
public class IcicleBlock extends Block {

	public static final MapCodec<IcicleBlock> CODEC = createCodec(IcicleBlock::new);
	private static final VoxelShape SHAPE = Block.createColumnShape(6.0, 2.0, 16.0);

	public IcicleBlock(AbstractBlock.Settings settings) {
		super(settings);
	}

	@Override
	public MapCodec<IcicleBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	@Override
	protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
		BlockPos above = pos.up();
		BlockState support = world.getBlockState(above);
		return support.isOf(this) || support.isSideSolidFullSquare(world, above, Direction.DOWN);
	}

	@Override
	protected BlockState getStateForNeighborUpdate(BlockState state, WorldView world, ScheduledTickView tickView, BlockPos pos,
			Direction direction, BlockPos neighborPos, BlockState neighborState, Random random) {
		if (direction == Direction.UP && !this.canPlaceAt(state, world, pos)) {
			return Blocks.AIR.getDefaultState();
		}
		return state;
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (random.nextInt(24) == 0 && world.getBlockState(pos.down()).isAir()) {
			world.addParticleClient(ParticleTypes.DRIPPING_WATER, pos.getX() + 0.5, pos.getY() + 0.05, pos.getZ() + 0.5,
					0.0, 0.0, 0.0);
		}
	}
}
