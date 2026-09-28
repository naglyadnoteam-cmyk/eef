package com.naglyadno.nedra.pressure;

/** Ярусы давления: 0 - безопасно, 5 - экстрим (без защиты опасно для жизни). */
public enum PressureTier {
	NONE(0),
	LOW(1),
	MODERATE(2),
	HIGH(3),
	SEVERE(4),
	EXTREME(5);

	public final int level;

	PressureTier(int level) {
		this.level = level;
	}

	/** value - давление 0..100. */
	public static PressureTier fromValue(double value) {
		if (value < 10) return NONE;
		if (value < 30) return LOW;
		if (value < 55) return MODERATE;
		if (value < 75) return HIGH;
		if (value < 92) return SEVERE;
		return EXTREME;
	}
}
