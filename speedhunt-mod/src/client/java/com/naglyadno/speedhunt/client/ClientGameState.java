package com.naglyadno.speedhunt.client;

import com.naglyadno.speedhunt.network.GameStatePayload;

/** Последнее состояние матча, полученное с сервера — читается HUD-оверлеем каждый кадр. */
public final class ClientGameState {

	private static volatile GameStatePayload latest;

	private ClientGameState() {
	}

	public static void update(GameStatePayload payload) {
		latest = payload;
	}

	public static void clear() {
		latest = null;
	}

	public static GameStatePayload latest() {
		return latest;
	}
}
