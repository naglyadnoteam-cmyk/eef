package com.naglyadno.nedra.block;

import com.naglyadno.nedra.block.entity.EchoOreBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Руда, которую почти не видно на глаз - её выдаёт только периодический звуковой "пинг" рядом.
 * Логика тика (проигрывание звука) вынесена в {@link EchoOreBlockEntity}.
 */
public class EchoOreBlock extends Block implements BlockEntityProvider {

	public EchoOreBlock(Settings settings) {
		super(settings);
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new EchoOreBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		if (world.isClient) {
			return null;
		}
		return validateTicker(type, ModBlocks.ECHO_ORE_ENTITY, EchoOreBlockEntity::serverTick);
	}
}
