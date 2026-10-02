package com.naglyadno.nedra.hazard;

import com.naglyadno.nedra.block.ModBlocks;
import com.naglyadno.nedra.config.NedraConfig;
import com.naglyadno.nedra.sound.ModSounds;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Обвалы. На глубине добыча рядом с нестабильным камнем (или, реже, просто в толще породы)
 * запускает обвал: сначала треск, гул и сыплющаяся с потолка пыль - время отбежать, - затем
 * несколько блоков свода падают вниз и ранят тех, кто остался под ними (шлем снижает урон).
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
			if (c.world().getRandom().nextInt(4) == 0) {
				dustFromCeiling(c.world(), c.origin(), 2);
			}
			return false;
		});
		for (ScheduledCollapse c : due) {
			doCollapse(c);
		}
	}

	public void onBlockBroken(ServerPlayerEntity player, BlockPos pos) {
		ServerWorld world = player.getEntityWorld();
		if (world.getRegistryKey() != World.OVERWORLD || player.isCreative() || player.isSpectator()) {
			return;
		}
		if (pos.getY() > config.surfaceY - 24) {
			return;
		}
		if (random.nextDouble() >= config.rockfallChancePerCheck) {
			return;
		}
		boolean nearUnstable = false;
		int r = config.rockfallCheckRadius;
		for (BlockPos p : BlockPos.iterate(pos.add(-r, -2, -r), pos.add(r, 4, r))) {
			if (world.getBlockState(p).isOf(ModBlocks.UNSTABLE_STONE)) {
				nearUnstable = true;
				break;
			}
		}
		if (!nearUnstable && random.nextInt(4) != 0) {
			return;
		}
		for (ScheduledCollapse c : scheduled) {
			if (c.origin().isWithinDistance(pos, 6)) {
				return;
			}
		}
		player.sendMessage(Text.translatable("message.nedra.rockfall.warning").formatted(Formatting.GOLD), true);
		world.playSound(null, pos, ModSounds.ROCKFALL_WARNING, SoundCategory.BLOCKS, 1.0f, 0.9f + random.nextFloat() * 0.2f);
		dustFromCeiling(world, pos, 6);
		scheduled.add(new ScheduledCollapse(tickCounter + config.rockfallWarningDelayTicks, world, pos.toImmutable(), player));
	}

	private void doCollapse(ScheduledCollapse c) {
		ServerWorld world = c.world();
		BlockPos center = c.origin();
		int dropped = 0;
		int attempts = 4 + random.nextInt(4);
		for (int i = 0; i < attempts; i++) {
			BlockPos above = findCeiling(world, center.add(random.nextInt(5) - 2, 0, random.nextInt(5) - 2));
			if (above == null) {
				continue;
			}
			BlockState state = world.getBlockState(above);
			BlockState falling = state.isOf(ModBlocks.UNSTABLE_STONE) ? Blocks.COBBLESTONE.getDefaultState() : state;
			FallingBlockEntity entity = FallingBlockEntity.spawnFromBlock(world, above, falling);
			entity.setHurtEntities(1.5f, 8);
			dropped++;
		}
		if (dropped == 0) {
			return;
		}
		world.spawnParticles(ParticleTypes.CLOUD, center.getX() + 0.5, center.getY() + 1.5, center.getZ() + 0.5,
				14, 1.5, 0.6, 1.5, 0.02);
		world.playSound(null, center, ModSounds.ROCKFALL_COLLAPSE, SoundCategory.BLOCKS, 1.6f, 0.9f + random.nextFloat() * 0.2f);
		if (!c.player().isRemoved()) {
			c.player().sendMessage(Text.translatable("message.nedra.rockfall.collapse").formatted(Formatting.RED, Formatting.BOLD), true);
		}
	}

	/** Ищет над колонной первый блок свода, который можно обрушить; null если свод крепкий/далеко. */
	private static BlockPos findCeiling(ServerWorld world, BlockPos column) {
		BlockPos.Mutable cursor = column.mutableCopy();
		for (int dy = 1; dy <= 7; dy++) {
			cursor.setY(column.getY() + dy);
			BlockState state = world.getBlockState(cursor);
			if (state.isAir()) {
				continue;
			}
			return canFall(world, cursor, state) && world.getBlockState(cursor.down()).isAir() ? cursor.toImmutable() : null;
		}
		return null;
	}

	private static boolean canFall(ServerWorld world, BlockPos pos, BlockState state) {
		if (state.hasBlockEntity() || !state.getFluidState().isEmpty()) {
			return false;
		}
		float hardness = state.getHardness(world, pos);
		return hardness >= 0 && hardness < 10 && state.isSolidBlock(world, pos);
	}

	private void dustFromCeiling(ServerWorld world, BlockPos origin, int count) {
		BlockPos ceiling = findCeiling(world, origin);
		BlockState dust = ceiling != null ? world.getBlockState(ceiling) : Blocks.STONE.getDefaultState();
		double y = ceiling != null ? ceiling.getY() - 0.05 : origin.getY() + 3;
		world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.FALLING_DUST, dust),
				origin.getX() + 0.5, y, origin.getZ() + 0.5, count, 1.6, 0.0, 1.6, 0.0);
	}
}
