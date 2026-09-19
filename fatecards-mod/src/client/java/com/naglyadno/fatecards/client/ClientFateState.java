package com.naglyadno.fatecards.client;

import com.naglyadno.fatecards.network.FateStatePayload;

/** Последнее состояние, полученное с сервера — читается HUD-оверлеем каждый кадр. */
public final class ClientFateState {

	private static volatile FateStatePayload latest;

	private ClientFateState() {
	}

	public static void update(FateStatePayload payload) {
		latest = payload;
	}

	public static void clear() {
		latest = null;
	}

	public static FateStatePayload latest() {
		return latest;
	}
}
