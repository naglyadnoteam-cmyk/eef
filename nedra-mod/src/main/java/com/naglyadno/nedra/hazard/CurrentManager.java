package com.naglyadno.nedra.hazard;

import com.naglyadno.nedra.block.CurrentVentBlock;
import com.naglyadno.nedra.block.ModBlocks;
import com.naglyadno.nedra.config.NedraConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Подземные течения. Открытый сверху воздушный разлом бьёт вверх столбом воздуха: игрока над ним
 * подхватывает и поднимает (а падение в такой столб гасит урон от падения). Проверяется только
 * маленькая колонна прямо под игроком - это дёшево, и поток действует только там, где его видно.
 */
public class CurrentManager {

	private final NedraConfig config;

	public CurrentManager(NedraConfig config) {
		this.config = config;
	}

	public void tick(MinecraftServer server) {
		ServerWorld world = server.getOverworld();
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player.isSpectator() || player.getAbilities().flying) {
				continue;
			}
			applyLift(world, player);
		}
	}

	private void applyLift(ServerWorld world, ServerPlayerEntity player) {
		BlockPos feet = player.getBlockPos();
		int height = config.currentColumnHeight;
		double bestStrength = 0.0;
		for (int dy = 0; dy <= height; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos pos = feet.add(dx, -dy - 1, dz);
					if (!world.getBlockState(pos).isOf(ModBlocks.CURRENT_VENT) || !CurrentVentBlock.isActive(world, pos)) {
						continue;
					}
					double horizontal = Math.hypot(player.getX() - (pos.getX() + 0.5), player.getZ() - (pos.getZ() + 0.5));
					if (horizontal > 1.2) {
						continue;
					}
					if (!isOpenColumn(world, pos, dy)) {
						continue;
					}
					double strength = config.currentLiftStrength * (1.0 - (double) dy / (height + 1));
					bestStrength = Math.max(bestStrength, strength);
				}
			}
		}
		if (bestStrength <= 0.0) {
			return;
		}
		Vec3d velocity = player.getVelocity();
		double targetUp = Math.min(0.55, velocity.y + bestStrength);
		player.setVelocity(velocity.x, targetUp, velocity.z);
		player.velocityModified = true;
		player.onLanding();
	}

	private static boolean isOpenColumn(World world, BlockPos vent, int height) {
		for (int i = 1; i <= height; i++) {
			if (world.getBlockState(vent.up(i)).isSolidBlock(world, vent.up(i))) {
				return false;
			}
		}
		return true;
	}
}
