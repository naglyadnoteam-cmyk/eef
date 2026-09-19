package com.naglyadno.fatecards.cards;

import com.naglyadno.fatecards.game.GameManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.Random;

/** Всё, что нужно эффекту карты: кто выбрал, кому достанется, сервер и планировщик отложенных действий. */
public record CardContext(
		MinecraftServer server,
		GameManager game,
		ServerPlayerEntity chooser,
		ServerPlayerEntity target,
		Random random
) {
	public ServerWorld world() {
		return target.getServerWorld();
	}
}
