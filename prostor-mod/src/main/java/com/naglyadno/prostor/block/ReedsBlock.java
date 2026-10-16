package com.naglyadno.prostor.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.Waterloggable;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldView;
import net.minecraft.world.tick.ScheduledTickView;

/**
 * Камыш (рогоз): высокое растение в два блока. Нижняя половина стоит в воде на мелководье
 * (или на берегу рядом с водой, как сахарный тростник), верхняя всегда над водой.
 */
public class ReedsBlock extends TallPlantBlock implements Waterloggable {

	public static final MapCodec<ReedsBlock> CODEC = createCodec(ReedsBlock::new);
	public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;
	private static final VoxelShape SHAPE = Block.createColumnShape(12.0, 0.0, 16.0);

	public ReedsBlock(AbstractBlock.Settings settings) {
		super(settings);
		setDefaultState(stateManager.getDefaultState().with(HALF, DoubleBlockHalf.LOWER).with(WATERLOGGED, false));
	}

	@Override
	public MapCodec<ReedsBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	/** Почва, на которой растёт камыш. */
	public static boolean isSoil(BlockState floor) {
		return floor.isIn(BlockTags.DIRT) || floor.isIn(BlockTags.SAND) || floor.isOf(Blocks.CLAY) || floor.isOf(Blocks.GRAVEL);
	}

	@Override
	protected boolean canPlantOnTop(BlockState floor, BlockView world, BlockPos pos) {
		if (!isSoil(floor)) {
			return false;
		}
		// стебель в стоячей воде - или на берегу, если вода рядом с почвой
		if (world.getFluidState(pos.up()).isEqualAndStill(Fluids.WATER)) {
			return true;
		}
		for (Direction direction : Direction.Type.HORIZONTAL) {
			if (world.getFluidState(pos.offset(direction)).isIn(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
		if (state.get(HALF) == DoubleBlockHalf.UPPER) {
			return super.canPlaceAt(state, world, pos);
		}
		BlockPos below = pos.down();
		return canPlantOnTop(world.getBlockState(below), world, below);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		BlockState state = super.getPlacementState(ctx);
		// верхняя половина не должна оказаться под водой
		if (state == null || ctx.getWorld().isWater(ctx.getBlockPos().up())) {
			return null;
		}
		return withWaterloggedState(ctx.getWorld(), ctx.getBlockPos(), state);
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
	}

	@Override
	protected BlockState getStateForNeighborUpdate(BlockState state, WorldView world, ScheduledTickView tickView,
			BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, Random random) {
		if (state.get(WATERLOGGED)) {
			tickView.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
		}
		return super.getStateForNeighborUpdate(state, world, tickView, pos, direction, neighborPos, neighborState, random);
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(HALF, WATERLOGGED);
	}
}
