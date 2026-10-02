package com.naglyadno.nedra.client;

import com.naglyadno.nedra.sound.ModSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.random.Random;

/**
 * Звуки давления, которые слышит только сам игрок: сердцебиение на высоких ярусах (его ритм
 * задаёт и пульсацию красной виньетки) и редкие стоны породы над головой.
 */
public final class ClientAmbience {

	private static final Random RANDOM = Random.create();
	private static long ticks;
	private static long lastBeatTick = -1000;
	private static long lastBeatNanos;

	private ClientAmbience() {
	}

	public static void tick(MinecraftClient client) {
		if (client.player == null || client.isPaused()) {
			return;
		}
		ticks++;
		int tier = ClientPressureState.tier();
		if (tier >= 4) {
			int period = tier >= 5 ? 16 : 24;
			if (ticks - lastBeatTick >= period) {
				lastBeatTick = ticks;
				lastBeatNanos = System.nanoTime();
				float volume = tier >= 5 ? 0.9f : 0.55f;
				client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.ENTITY_WARDEN_HEARTBEAT, 1.0f, volume));
			}
		}
		if (tier >= 3 && RANDOM.nextInt(20 * 14) == 0) {
			float volume = 0.35f + 0.12f * (tier - 3);
			client.getSoundManager().play(PositionedSoundInstance.ambient(ModSounds.PRESSURE_GROAN,
					0.85f + RANDOM.nextFloat() * 0.3f, volume));
		}
	}

	/** 1.0 в момент удара сердца, быстро затухает к 0 - для синхронной пульсации HUD. */
	public static float pulse() {
		if (ClientPressureState.tier() < 4) {
			return 0f;
		}
		float seconds = (System.nanoTime() - lastBeatNanos) / 1_000_000_000f;
		return (float) Math.exp(-seconds / 0.18);
	}

	public static void reset() {
		lastBeatTick = -1000;
	}
}
