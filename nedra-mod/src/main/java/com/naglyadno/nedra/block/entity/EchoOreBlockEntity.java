package com.naglyadno.nedra.block.entity;

import com.naglyadno.nedra.NedraMod;
import com.naglyadno.nedra.block.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Раз в N тиков проигрывает тихий "пинг" в мир - его можно услышать (направленно, по громкости), но не увидеть. */
public class EchoOreBlockEntity extends BlockEntity {

	private int ticksUntilPing;

	public EchoOreBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.ECHO_ORE_ENTITY, pos, state);
		this.ticksUntilPing = 20 + pos.hashCode() % 40;
	}

	public static void serverTick(World world, BlockPos pos, BlockState state, EchoOreBlockEntity entity) {
		if (!(world instanceof ServerWorld serverWorld)) {
			return;
		}
		entity.ticksUntilPing--;
		if (entity.ticksUntilPing <= 0) {
			entity.ticksUntilPing = NedraMod.config().echoOrePingIntervalTicks;
			serverWorld.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.AMBIENT,
					0.6f, 0.5f + serverWorld.getRandom().nextFloat() * 0.3f);
		}
	}
}
