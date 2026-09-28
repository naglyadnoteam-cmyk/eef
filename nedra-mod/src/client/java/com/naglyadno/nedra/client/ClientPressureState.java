package com.naglyadno.nedra.client;

/** Последнее полученное с сервера состояние давления - хранится статично, читается HUD-рендером каждый кадр. */
public final class ClientPressureState {

	private static volatile double pressure = 0.0;
	private static volatile int tier = 0;

	private ClientPressureState() {
	}

	public static void update(double newPressure, int newTier) {
		pressure = newPressure;
		tier = newTier;
	}

	public static void clear() {
		pressure = 0.0;
		tier = 0;
	}

	public static double pressure() {
		return pressure;
	}

	public static int tier() {
		return tier;
	}
}
