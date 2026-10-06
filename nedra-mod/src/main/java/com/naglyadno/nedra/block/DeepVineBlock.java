package com.naglyadno.nedra.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.tick.ScheduledTickView;
import org.jspecify.annotations.Nullable;

/**
 * Глубинная лиана: свисает со свода Заросших глубин цепочкой блоков. Нижний блок - кончик с завитком,
 * иногда с налитой светящейся спорой. По лиане можно лазить (тег minecraft:climbable). Держится только
 * за твёрдый низ блока сверху или за лиану; без опоры осыпается вместе со всем, что висит ниже.
 */
public class DeepVineBlock extends Block {

	public static final MapCodec<DeepVineBlock> CODEC = createCodec(DeepVineBlock::new);
	public static final BooleanProperty TIP = BooleanProperty.of("tip");
	public static final BooleanProperty BLOOM = BooleanProperty.of("bloom");
	private static final VoxelShape SHAPE = Block.createColumnShape(12.0, 0.0, 16.0);

	public DeepVineBlock(AbstractBlock.Settings settings) {
		super(settings);
		this.setDefaultState(this.stateManager.getDefaultState().with(TIP, true).with(BLOOM, false));
	}

	@Override
	public MapCodec<DeepVineBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(TIP, BLOOM);
	}

	/** Светится только кончик со спорой. */
	public static int luminance(BlockState state) {
		return state.get(TIP) && state.get(BLOOM) ? 10 : 0;
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
	public @Nullable BlockState getPlacementState(ItemPlacementContext ctx) {
		BlockState below = ctx.getWorld().getBlockState(ctx.getBlockPos().down());
		return this.getDefaultState().with(TIP, !below.isOf(this));
	}

	@Override
	protected BlockState getStateForNeighborUpdate(BlockState state, WorldView world, ScheduledTickView tickView, BlockPos pos,
			Direction direction, BlockPos neighborPos, BlockState neighborState, Random random) {
		if (direction == Direction.UP && !this.canPlaceAt(state, world, pos)) {
			return Blocks.AIR.getDefaultState();
		}
		if (direction == Direction.DOWN) {
			boolean tip = !neighborState.isOf(this);
			// спора бывает только на кончике: если под ним выросла лиана, спора пропадает
			return state.with(TIP, tip).with(BLOOM, tip && state.get(BLOOM));
		}
		return state;
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (state.get(TIP) && state.get(BLOOM) && random.nextInt(4) == 0) {
			world.addParticleClient(ParticleTypes.SPORE_BLOSSOM_AIR,
					pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.3, pos.getY() + 0.1,
					pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.3, 0.0, 0.0, 0.0);
		}
	}
}
