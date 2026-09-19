package com.naglyadno.fatecards.cards;

import net.minecraft.util.Formatting;

/** Категории карт судьбы — влияют только на цвет отображения и вес редкости. */
public enum Category {
	STATS("Характеристики", Formatting.AQUA),
	PHYSICS("Физика", Formatting.LIGHT_PURPLE),
	WORLD("Мир", Formatting.GREEN),
	MOBS("Мобы", Formatting.RED),
	TELEPORT("Телепортация", Formatting.BLUE),
	INVENTORY("Инвентарь", Formatting.YELLOW),
	WEIRD("Странности", Formatting.DARK_PURPLE),
	LEGENDARY("Легендарное", Formatting.GOLD);

	private final String displayName;
	private final Formatting color;

	Category(String displayName, Formatting color) {
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
