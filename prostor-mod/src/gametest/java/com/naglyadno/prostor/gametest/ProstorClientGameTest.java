package com.naglyadno.prostor.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/** Проверка мода в настоящем клиенте: заходит в мир, снимает скриншоты и печатает замеры STATS|. */
public class ProstorClientGameTest implements FabricClientGameTest {

	private static final Logger LOGGER = LoggerFactory.getLogger("prostor-gametest");

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> {
			// размытие фона меню на программном OpenGL (CI без видеокарты) занимает секунды на кадр
			client.options.getMenuBackgroundBlurriness().setValue(0);
			client.options.getViewDistance().setValue(10);
			client.options.getSimulationDistance().setValue(5);
			client.options.language = "ru_ru";
			client.getLanguageManager().setLanguage("ru_ru");
			client.reloadResources();
		});
		context.waitFor(client -> client.getOverlay() == null);
		try (TestSingleplayerContext singleplayer = context.worldBuilder()
				.adjustSettings(creator -> creator.setSeed("prostor"))
				.create()) {
			TestServerContext server = singleplayer.getServer();
			singleplayer.getClientWorld().waitForChunksRender();
			server.runCommand("difficulty peaceful");
			server.runCommand("gamerule doDaylightCycle false");
			server.runCommand("gamerule doWeatherCycle false");
			server.runCommand("time set 1000");
			server.runCommand("weather clear");
			server.runCommand("effect give @a minecraft:resistance infinite 255 true");

			server.runOnServer(BiomeSurvey::report);

			// обзор спавна с высоты
			int[] spawn = server.computeOnServer(ProstorClientGameTest::spawnSurface);
			tp(server, spawn[0] + 0.5, spawn[1] + 30, spawn[2] + 0.5, 135, 25);
			context.waitTicks(100);
			singleplayer.getClientWorld().waitForChunksRender();
			shot(context, "prostor_00_spawn_overview");
		}
	}

	private static int[] spawnSurface(MinecraftServer server) {
		ServerWorld world = server.getOverworld();
		BlockPos spawn = server.getPlayerManager().getPlayerList().get(0).getBlockPos();
		int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
		return new int[]{spawn.getX(), y, spawn.getZ()};
	}

	/** Телепорт с дробными координатами: формат через Locale.ROOT, без "%d.5" (иначе ломается на отрицательных). */
	static void tp(TestServerContext server, double x, double y, double z, float yaw, float pitch) {
		server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
	}

	static void shot(ClientGameTestContext context, String name) {
		context.runOnClient(client -> {
			client.getToastManager().clear();
			client.inGameHud.getChatHud().clear(false);
		});
		context.waitTicks(2);
		context.takeScreenshot(name);
		LOGGER.info("STATS| screenshot {}", name);
	}
}
