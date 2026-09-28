package com.naglyadno.chunkrange;

import net.fabricmc.api.ClientModInitializer;

/** Мод сам по себе ничего не делает при инициализации — вся работа в GameOptionsMixin, который расширяет слайдеры. */
public class ChunkRangeClient implements ClientModInitializer {
	public static final String MOD_ID = "chunkrange";
	public static final int MAX_DISTANCE = 256;

	@Override
	public void onInitializeClient() {
	}
}
