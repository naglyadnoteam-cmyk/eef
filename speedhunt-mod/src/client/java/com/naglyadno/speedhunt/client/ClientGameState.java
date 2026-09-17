package com.naglyadno.speedhunt.client;

import com.naglyadno.speedhunt.network.GameStatePayload;

/** Последнее состояние матча, полученное с сервера — читается HUD-оверлеем каждый кадр. */
public final class ClientGameState {

	private static volatile GameStatePayload latest;
	private static volatile String bannerText;
	private static volatile long bannerShownAtMs;
	private static final long BANNER_DURATION_MS = 6000;

	private ClientGameState() {
	}

	public static void update(GameStatePayload payload) {
		latest = payload;
	}

	public static void clear() {
		latest = null;
		bannerText = null;
	}

	public static GameStatePayload latest() {
		return latest;
	}

	public static void showBanner(String text) {
		bannerText = text;
		bannerShownAtMs = System.currentTimeMillis();
	}

	/** @return активный текст баннера, либо null если ничего не показывать. */
	public static String activeBanner() {
		String text = bannerText;
		if (text == null) {
			return null;
		}
		return (System.currentTimeMillis() - bannerShownAtMs < BANNER_DURATION_MS) ? text : null;
	}
}
