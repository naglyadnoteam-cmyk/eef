package com.naglyadno.nedra.hazard;

import com.naglyadno.nedra.block.ModBlocks;
import com.naglyadno.nedra.config.NedraConfig;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.particle.ParticleTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * На глубине добыча блоков рядом с нестабильным камнем может запустить обвал: сперва
 * предупреждающий треск и осыпающиеся частицы, затем (через задержку) реальное падение блоков.
 */
public class RockfallManager {

	private final NedraConfig config;
	private final Random random = Random.create();
	private final List<ScheduledCollapse> scheduled = new ArrayList<>();
	private long tickCounter;

	public RockfallManager(NedraConfig config) {
		this.config = config;
	}

	private record ScheduledCollapse(long dueTick, ServerWorld world, BlockPos origin, ServerPlayerEntity player) {
	}

	public void tick() {
		tickCounter++;
		if (scheduled.isEmpty()) {
			return;
		}
		List<ScheduledCollapse> due = new ArrayList<>();
		scheduled.removeIf(c -> {
			if (tickCounter >= c.dueTick()) {
				due.add(c);
				return true;
			}
			return false;
		});
		for (ScheduledCollapse c : due) {
			doCollapse(c);
		}
	}

	public void onBlockBroken(ServerPlayerEntity player, BlockPos pos) {
		if (player.getBlockY() > config.surfaceY - 24) {
			return;
		}
		if (random.nextDouble() >= config.rockfallChancePerCheck) {
			return;
		}
		ServerWorld world = player.getEntityWorld();
		boolean nearUnstable = false;
		for (BlockPos p : BlockPos.iterate(pos.add(-config.rockfallCheckRadius, -2, -config.rockfallCheckRadius),
				pos.add(config.rockfallCheckRadius, 4, config.rockfallCheckRadius))) {
			if (world.getBlockState(p).isOf(ModBlocks.UNSTABLE_STONE)) {
				nearUnstable = true;
				break;
			}
		}
		if (!nearUnstable && random.nextInt(4) != 0) {
			return;
		}
		player.sendMessage(Text.literal("Вы слышите треск камня над головой...").formatted(Formatting.GRAY), true);
		world.playSound(null, pos, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 0.8f, 0.7f);
		scheduled.add(new ScheduledCollapse(tickCounter + config.rockfallWarningDelayTicks, world, pos.toImmutable(), player));
	}

	private void doCollapse(ScheduledCollapse c) {
		if (!c.player().isAlive() || c.player().isRemoved()) {
			return;
		}
		ServerWorld world = c.world();
		BlockPos center = c.origin();
		int count = 3 + random.nextInt(4);
		for (int i = 0; i < count; i++) {
			BlockPos above = center.add(random.nextInt(5) - 2, 4 + random.nextInt(3), random.nextInt(5) - 2);
			net.minecraft.block.BlockState state = world.getBlockState(above);
			Block block = state.getBlock();
			if (state.isAir() || block == Blocks.BEDROCK || state.getHardness(world, above) < 0) {
				continue;
			}
			world.setBlockState(above, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_ALL);
			FallingBlockEntity.spawnFromBlock(world, above, state.isOf(ModBlocks.UNSTABLE_STONE)
					? Blocks.COBBLESTONE.getDefaultState()
					: state);
		}
		world.spawnParticles(ParticleTypes.CLOUD, center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5,
				12, 1.2, 0.5, 1.2, 0.02);
		world.playSound(null, center, SoundEvents.BLOCK_GRAVEL_FALL, SoundCategory.BLOCKS, 1.2f, 0.6f);
		c.player().sendMessage(Text.literal("Обвал!").formatted(Formatting.RED, Formatting.BOLD), true);
	}
}
