package com.naglyadno.fatecards.cards;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Общие строительные блоки для эффектов карт: толчки, безопасная телепортация, правка мира, мобы, предметы. */
public final class CardEffects {

	private CardEffects() {
	}

	// ------------------------------------------------------------------
	// Импульс
	// ------------------------------------------------------------------

	public static void impulse(ServerPlayerEntity player, double dx, double dy, double dz) {
		player.setVelocity(player.getVelocity().add(dx, dy, dz));
		player.velocityModified = true;
		player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
	}

	public static void impulseUp(ServerPlayerEntity player, double power) {
		impulse(player, 0, power, 0);
	}

	// ------------------------------------------------------------------
	// Телепортация
	// ------------------------------------------------------------------

	public static void teleportSafe(ServerPlayerEntity player, ServerWorld world, double x, double z) {
		int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
		player.teleport(world, x, y + 1, z, Set.of(), player.getYaw(), player.getPitch());
	}

	public static void teleportUp(ServerPlayerEntity player, double amount) {
		player.teleport(player.getServerWorld(), player.getX(), player.getY() + amount, player.getZ(),
				Set.of(), player.getYaw(), player.getPitch());
	}

	public static void clearAirPocket(ServerWorld world, BlockPos center, int radius, int heightUp) {
		for (BlockPos pos : BlockPos.iterate(center.add(-radius, 0, -radius), center.add(radius, heightUp, radius))) {
			if (!world.getBlockState(pos).isOf(Blocks.BEDROCK)) {
				world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
			}
		}
	}

	// ------------------------------------------------------------------
	// Правка блоков
	// ------------------------------------------------------------------

	public static void areaReplace(ServerWorld world, BlockPos center, int radius, int depth, BlockState state, boolean skipAir) {
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				BlockPos top = new BlockPos(center.getX() + dx, center.getY() - 1, center.getZ() + dz);
				for (int dy = 0; dy < depth; dy++) {
					BlockPos pos = top.down(dy);
					BlockState existing = world.getBlockState(pos);
					if (existing.isOf(Blocks.BEDROCK)) {
						continue;
					}
					if (skipAir && existing.isAir()) {
						continue;
					}
					world.setBlockState(pos, state, Block.NOTIFY_ALL);
				}
			}
		}
	}

	public static Map<BlockPos, BlockState> areaReplaceSnapshot(ServerWorld world, BlockPos center, int radius, int depth, BlockState state) {
		Map<BlockPos, BlockState> snapshot = new HashMap<>();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				BlockPos top = new BlockPos(center.getX() + dx, center.getY() - 1, center.getZ() + dz);
				for (int dy = 0; dy < depth; dy++) {
					BlockPos pos = top.down(dy);
					BlockState existing = world.getBlockState(pos);
					if (existing.isOf(Blocks.BEDROCK)) {
						continue;
					}
					snapshot.put(pos.toImmutable(), existing);
					world.setBlockState(pos, state, Block.NOTIFY_ALL);
				}
			}
		}
		return snapshot;
	}

	public static void restoreSnapshot(ServerWorld world, Map<BlockPos, BlockState> snapshot) {
		for (Map.Entry<BlockPos, BlockState> entry : snapshot.entrySet()) {
			world.setBlockState(entry.getKey(), entry.getValue(), Block.NOTIFY_ALL);
		}
	}

	public static void clearArea(ServerWorld world, BlockPos center, int radius, int depth) {
		areaReplace(world, center, radius, depth, Blocks.AIR.getDefaultState(), false);
	}

	public static void clearCircle(ServerWorld world, BlockPos center, int radius, int depth) {
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx * dx + dz * dz > radius * radius) {
					continue;
				}
				BlockPos top = new BlockPos(center.getX() + dx, center.getY() - 1, center.getZ() + dz);
				for (int dy = 0; dy < depth; dy++) {
					BlockPos pos = top.down(dy);
					if (!world.getBlockState(pos).isOf(Blocks.BEDROCK)) {
						world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
					}
				}
			}
		}
	}

	public static void placeColumn(ServerWorld world, BlockPos base, int height, BlockState state) {
		for (int i = 0; i < height; i++) {
			world.setBlockState(base.up(i), state, Block.NOTIFY_ALL);
		}
	}

	// ------------------------------------------------------------------
	// Мобы
	// ------------------------------------------------------------------

	public static void spawnMobs(ServerWorld world, BlockPos center, EntityType<? extends MobEntity> type,
			int count, int radius, boolean hostile, ServerPlayerEntity target, Random random) {
		for (int i = 0; i < count; i++) {
			double x = center.getX() + 0.5 + (random.nextDouble() * 2 - 1) * radius;
			double z = center.getZ() + 0.5 + (random.nextDouble() * 2 - 1) * radius;
			int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int) x, (int) z);
			MobEntity mob = type.create(world);
			if (mob == null) {
				continue;
			}
			mob.refreshPositionAndAngles(x, y + 1, z, random.nextFloat() * 360f, 0f);
			world.spawnEntity(mob);
			if (hostile && target != null) {
				mob.setTarget(target);
			}
		}
	}

	public static void spawnMobsAirborne(ServerWorld world, BlockPos center, EntityType<? extends MobEntity> type,
			int count, int radius, int heightAbove, Random random) {
		for (int i = 0; i < count; i++) {
			double x = center.getX() + 0.5 + (random.nextDouble() * 2 - 1) * radius;
			double z = center.getZ() + 0.5 + (random.nextDouble() * 2 - 1) * radius;
			MobEntity mob = type.create(world);
			if (mob == null) {
				continue;
			}
			mob.refreshPositionAndAngles(x, center.getY() + heightAbove, z, random.nextFloat() * 360f, 0f);
			world.spawnEntity(mob);
		}
	}

	// ------------------------------------------------------------------
	// Падающие блоки
	// ------------------------------------------------------------------

	public static void spawnFallingBlocks(ServerWorld world, BlockPos center, List<Block> palette, int count,
			int spread, int heightAbove, Random random) {
		for (int i = 0; i < count; i++) {
			Block block = palette.get(random.nextInt(palette.size()));
			BlockPos pos = center.add(
					random.nextInt(spread * 2 + 1) - spread,
					heightAbove,
					random.nextInt(spread * 2 + 1) - spread
			);
			FallingBlockEntity.spawnFromBlock(world, pos, block.getDefaultState());
		}
	}

	// ------------------------------------------------------------------
	// Взрыв
	// ------------------------------------------------------------------

	public static void explode(ServerWorld world, double x, double y, double z, float power, boolean fire) {
		world.createExplosion(null, x, y, z, power, fire, World.ExplosionSourceType.MOB);
	}

	// ------------------------------------------------------------------
	// Инвентарь
	// ------------------------------------------------------------------

	public static void giveItem(ServerPlayerEntity player, ItemStack stack) {
		if (!player.getInventory().insertStack(stack)) {
			player.dropItem(stack, false);
		}
	}

	public static List<Integer> nonEmptySlots(ServerPlayerEntity player) {
		List<Integer> slots = new ArrayList<>();
		for (int i = 0; i < player.getInventory().main.size(); i++) {
			if (!player.getInventory().main.get(i).isEmpty()) {
				slots.add(i);
			}
		}
		return slots;
	}
}
