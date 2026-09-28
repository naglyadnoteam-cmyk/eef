package com.naglyadno.nedra.hazard;

import com.naglyadno.nedra.block.ModBlocks;
import com.naglyadno.nedra.config.NedraConfig;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Блок "current_vent" раз в тик слегка толкает игроков в радиусе вдоль своей грани (направление
 * потока = сторона света, в которую он "смотрит" своей текстурой - для простоты берём Direction.UP
 * как базовый поток и добавляем горизонтальный снос по хешу позиции, чтобы потоки не были одинаковыми).
 */
public class CurrentManager {

	private final NedraConfig config;
	private long tickCounter;

	public CurrentManager(NedraConfig config) {
		this.config = config;
	}

	public void tick(MinecraftServer server) {
		tickCounter++;
		if (tickCounter % 5 != 0) {
			return;
		}
		for (ServerWorld world : server.getWorlds()) {
			if (world.getRegistryKey() != World.OVERWORLD) {
				continue;
			}
			for (ServerPlayerEntity player : world.getPlayers()) {
				applyNearbyCurrents(world, player);
			}
		}
	}

	private void applyNearbyCurrents(ServerWorld world, ServerPlayerEntity player) {
		BlockPos base = player.getBlockPos();
		int r = config.currentRadius;
		for (BlockPos pos : BlockPos.iterate(base.add(-r, -r, -r), base.add(r, r, r))) {
			if (!world.getBlockState(pos).isOf(ModBlocks.CURRENT_VENT)) {
				continue;
			}
			double dist = Math.sqrt(pos.getSquaredDistance(player.getEntityPos()));
			if (dist > r || dist < 0.01) {
				continue;
			}
			Direction flow = flowDirection(pos);
			double falloff = 1.0 - dist / r;
			Vec3d push = new Vec3d(flow.getOffsetX(), flow.getOffsetY(), flow.getOffsetZ())
					.multiply(config.currentPushStrength * falloff);
			player.setVelocity(player.getVelocity().add(push));
			player.velocityDirty = true;
			if (world.getRandom().nextInt(4) == 0) {
				world.spawnParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
						2, 0.3, 0.3, 0.3, 0.01);
			}
		}
	}

	private Direction flowDirection(BlockPos pos) {
		Direction[] horizontal = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
		int idx = Math.floorMod(pos.hashCode(), horizontal.length);
		return horizontal[idx];
	}
}
