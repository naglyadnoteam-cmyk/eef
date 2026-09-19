package com.naglyadno.fatecards.cards;

@FunctionalInterface
public interface CardEffect {
	void apply(CardContext ctx);
}
