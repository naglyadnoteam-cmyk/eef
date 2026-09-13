package com.naglyadno.speedhunt.game;

import net.minecraft.util.Formatting;

/** Роль игрока в текущем матче. */
public enum Role {
	NONE("Не участвует", Formatting.GRAY),
	SPEEDRUNNER("Спидраннер", Formatting.GREEN),
	HUNTER("Охотник", Formatting.RED),
	SPECTATOR("Зритель", Formatting.GRAY);

	private final String displayName;
	private final Formatting color;

	Role(String displayName, Formatting color) {
		this.displayName = displayName;
		this.color = color;
	}

	public String displayName() {
		return displayName;
	}

	public Formatting color() {
		return color;
	}
}
