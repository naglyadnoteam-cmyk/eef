package com.naglyadno.fatecards.cards;

/** Одна карта судьбы: имя, описание, категория, вес редкости и сам эффект. */
public final class Card {

	private final String id;
	private final String name;
	private final String description;
	private final Category category;
	private final int weight;
	private final CardEffect effect;

	public Card(String id, String name, String description, Category category, int weight, CardEffect effect) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.category = category;
		this.weight = weight;
		this.effect = effect;
	}

	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	public String description() {
		return description;
	}

	public Category category() {
		return category;
	}

	public int weight() {
		return weight;
	}

	public void apply(CardContext ctx) {
		effect.apply(ctx);
	}
}
