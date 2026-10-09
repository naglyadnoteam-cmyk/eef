package com.naglyadno.nedra.hazard;

import com.naglyadno.nedra.network.RiverFlowPayload;
import com.naglyadno.nedra.worldgen.deep.DeepTerrain;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Течение подземных рек. Направление считается по форме русла (вода идёт туда, где ниже ступени, -
 * туда же падают водопады). Своего игрока клиент сносит сам, плавно (сервер присылает направление),
 * а предметы, лодки и мобов в реке сносит сервер.
 */
public class RiverCurrentManager {

	/** Прибавка скорости за тик для предметов и мобов в реке. */
	private static final double ENTITY_PUSH = 0.016;
	private static final int ENTITY_RADIUS = 32;

	private final Map<UUID, Boolean> active = new HashMap<>();
	private long ticks;

	public void tick(MinecraftServer server) {
		ticks++;
		ServerWorld world = server.getOverworld();
		DeepTerrain terrain = DeepTerrain.of(world.getSeed());
		Set<Entity> pushed = new HashSet<>();
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player.getY() > DeepTerrain.TOP + 4 || player.getY() < DeepTerrain.FLOOR) {
				sendIfChanged(player, null);
				continue;
			}
			double[] flow = player.isTouchingWater() && !player.isSpectator()
					? flowAt(terrain, player.getX(), player.getY(), player.getZ()) : null;
			sendIfChanged(player, flow);
			if (player.isSpectator()) {
				continue;
			}
			Box area = player.getBoundingBox().expand(ENTITY_RADIUS);
			for (Entity entity : world.getOtherEntities(player, area, e -> !(e instanceof PlayerEntity) && e.isTouchingWater())) {
				if (!pushed.add(entity)) {
					continue;
				}
				double[] f = flowAt(terrain, entity.getX(), entity.getY(), entity.getZ());
				if (f != null) {
					entity.addVelocity(f[0] * ENTITY_PUSH, 0.0, f[1] * ENTITY_PUSH);
				}
			}
		}
	}

	public void onDisconnect(ServerPlayerEntity player) {
		active.remove(player.getUuid());
	}

	/** Направление течения в точке или null, если точка не в воде подземной реки. */
	public static double[] flowAt(DeepTerrain terrain, double x, double y, double z) {
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		for (int which = 1; which <= 2; which++) {
			DeepTerrain.River river = terrain.river(which, bx, bz);
			if (river.strength() <= 0.05) {
				continue;
			}
			if (y >= river.waterY() - river.depth() - 1 && y <= river.waterY() + 1.0) {
				return terrain.riverFlow(which, x, z);
			}
		}
		return null;
	}

	private void sendIfChanged(ServerPlayerEntity player, double[] flow) {
		boolean now = flow != null;
		Boolean before = active.get(player.getUuid());
		// направление меняется плавно вдоль русла, поэтому, пока игрок в реке, обновляем его раз в 10 тиков
		if (before != null && before == now && (!now || ticks % 10 != 0)) {
			return;
		}
		active.put(player.getUuid(), now);
		ServerPlayNetworking.send(player, now
				? new RiverFlowPayload((float) flow[0], (float) flow[1], true)
				: new RiverFlowPayload(0.0F, 0.0F, false));
	}
}
