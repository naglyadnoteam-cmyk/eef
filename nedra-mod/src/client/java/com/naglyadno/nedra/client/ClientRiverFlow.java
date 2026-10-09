package com.naglyadno.nedra.client;

import com.naglyadno.nedra.network.RiverFlowPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.particle.ParticleTypes;

/**
 * Течение подземной реки для своего игрока: сносит по течению каждый тик, пока игрок в воде, и
 * пускает по воде мелкие брызги в сторону течения, чтобы его было видно.
 */
public final class ClientRiverFlow {

	/** Прибавка скорости за тик: против течения можно плыть, но медленно. */
	private static final double PUSH = 0.016;

	private static float dx;
	private static float dz;
	private static boolean active;

	private ClientRiverFlow() {
	}

	public static void update(RiverFlowPayload payload) {
		dx = payload.dx();
		dz = payload.dz();
		active = payload.active();
	}

	public static void reset() {
		active = false;
	}

	public static void tick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (!active || player == null || client.isPaused() || player.isSpectator() || player.getAbilities().flying
				|| !player.isTouchingWater()) {
			return;
		}
		player.addVelocity(dx * PUSH, 0.0, dz * PUSH);
		if (player.getRandom().nextInt(3) == 0) {
			double ox = (player.getRandom().nextDouble() - 0.5) * 3.0;
			double oz = (player.getRandom().nextDouble() - 0.5) * 3.0;
			player.getEntityWorld().addParticleClient(ParticleTypes.BUBBLE, player.getX() + ox, player.getY() + 0.2,
					player.getZ() + oz, dx * 0.2, 0.0, dz * 0.2);
		}
	}
}
