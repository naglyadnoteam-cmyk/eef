package com.naglyadno.nedra.client;

import com.naglyadno.nedra.network.ConfigSyncPayload;
import com.naglyadno.nedra.network.PressurePayload;

/**
 * Состояние давления на клиенте. Сервер присылает значения раз в полсекунды, а HUD рисует
 * плавно интерполированные "отображаемые" значения, чтобы шкала не прыгала скачками.
 */
public final class ClientPressureState {

	private static float targetEffective;
	private static float targetRaw;
	private static int protection;
	private static int tier;
	private static boolean active;

	private static float shownEffective;
	private static float shownRaw;
	private static float visibility;
	private static long lastFrameNanos;

	private static ConfigSyncPayload config = new ConfigSyncPayload(20, 45, 75, 25, 180, 32);

	private ClientPressureState() {
	}

	public static void update(PressurePayload payload) {
		targetEffective = payload.effective();
		targetRaw = payload.raw();
		protection = payload.protection();
		tier = payload.tier();
		active = payload.active();
	}

	public static void updateConfig(ConfigSyncPayload payload) {
		config = payload;
	}

	public static void clear() {
		targetEffective = targetRaw = shownEffective = shownRaw = visibility = 0f;
		protection = tier = 0;
		active = false;
	}

	/** Продвигает анимацию; вызывается раз за кадр из HUD. */
	public static void animate() {
		long now = System.nanoTime();
		float dt = lastFrameNanos == 0 ? 0f : Math.min(0.1f, (now - lastFrameNanos) / 1_000_000_000f);
		lastFrameNanos = now;
		float k = 1f - (float) Math.exp(-dt * 6.0);
		shownEffective += (targetEffective - shownEffective) * k;
		shownRaw += (targetRaw - shownRaw) * k;
		float visibleTarget = active && targetRaw > 0.5f ? 1f : 0f;
		float step = dt * 2.5f;
		visibility = visibility < visibleTarget ? Math.min(visibleTarget, visibility + step) : Math.max(visibleTarget, visibility - step);
	}

	public static float effective() {
		return shownEffective;
	}

	public static float raw() {
		return shownRaw;
	}

	public static int protection() {
		return protection;
	}

	public static int tier() {
		return active ? tier : 0;
	}

	public static float visibility() {
		return visibility;
	}

	public static ConfigSyncPayload config() {
		return config;
	}
}
