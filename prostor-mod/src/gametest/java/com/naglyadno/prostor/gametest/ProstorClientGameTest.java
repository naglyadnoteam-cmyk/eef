package com.naglyadno.prostor.gametest;

import com.naglyadno.prostor.command.ProstorCommands;
import com.naglyadno.prostor.worldgen.Lakes;
import com.naglyadno.prostor.worldgen.ProstorBiomes;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/** Проверка мода в настоящем клиенте: заходит в мир, снимает скриншоты и печатает замеры STATS|. */
public class ProstorClientGameTest implements FabricClientGameTest {

	private static final Logger LOGGER = LoggerFactory.getLogger("prostor-gametest");
	/** Ожидание отрисовки чанков: программный OpenGL на CI медленный. */
	private static final int RENDER_TIMEOUT = 2400;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> {
			// размытие фона меню на программном OpenGL (CI без видеокарты) занимает секунды на кадр
			client.options.getMenuBackgroundBlurriness().setValue(0);
			client.options.getViewDistance().setValue(6);
			client.options.getSimulationDistance().setValue(5);
			client.options.hudHidden = true;
			client.options.language = "ru_ru";
			client.getLanguageManager().setLanguage("ru_ru");
			client.reloadResources();
		});
		context.waitFor(client -> client.getOverlay() == null);
		// обычный мир (по умолчанию тестовый мир плоский)
		try (TestSingleplayerContext singleplayer = context.worldBuilder()
				.setUseConsistentSettings(false)
				.adjustSettings(creator -> creator.setSeed("prostor"))
				.create()) {
			TestServerContext server = singleplayer.getServer();
			render(singleplayer);
			server.runCommand("difficulty peaceful");
			server.runCommand("gamerule doDaylightCycle false");
			server.runCommand("gamerule doWeatherCycle false");
			server.runCommand("gamerule doMobSpawning false");
			server.runCommand("time set 1500");
			server.runCommand("weather clear");
			server.runCommand("effect give @a minecraft:resistance infinite 255 true");
			server.runCommand("effect give @a minecraft:saturation infinite 255 true");
			server.runCommand("gamemode spectator @a");

			server.runOnServer(BiomeSurvey::report);

			// ---- Озёрный край: команда сама ставит на берег ближайшего озера лицом к воде
			server.runCommand("execute as @a at @s run prostor tp lake_district");
			context.waitTicks(40);
			render(singleplayer);
			int[] view = server.computeOnServer(ProstorClientGameTest::lakeView);
			if (view == null) {
				LOGGER.info("STATS| lake_district: no lake viewpoint found");
				shot(context, "prostor_01_lake_district");
				return;
			}
			int vx = view[0], vy = view[1], vz = view[2], cx = view[3], cz = view[4], water = view[5];
			float yaw = yawTo(vx + 0.5, vz + 0.5, cx + 0.5, cz + 0.5);
			tp(server, vx + 0.5, vy + 0.4, vz + 0.5, yaw, 10);
			context.waitTicks(60);
			render(singleplayer);
			shot(context, "prostor_01_lake_shore");

			// вид сверху: несколько озёр между холмами
			double back = 26;
			double bx = vx + 0.5 - (cx - vx) / dist(vx, vz, cx, cz) * back;
			double bz = vz + 0.5 - (cz - vz) / dist(vx, vz, cx, cz) * back;
			tp(server, bx, water + 34, bz, yaw, 32);
			context.waitTicks(60);
			render(singleplayer);
			shot(context, "prostor_02_lake_aerial");

			// вид с воды на берег: заросли камыша, кувшинки, тростник
			double sx = cx + 0.5 + (vx - cx) * 0.3;
			double sz = cz + 0.5 + (vz - cz) * 0.3;
			tp(server, sx, water + 2.2, sz, yawTo(sx, sz, vx + 0.5, vz + 0.5), 14);
			context.waitTicks(40);
			render(singleplayer);
			shot(context, "prostor_03_reeds");

			// закат над озером: встаём на восточный берег и смотрим на запад, на солнце
			int[] west = server.computeOnServer(s -> sunsetView(s, cx, cz));
			server.runCommand("time set 12000");
			if (west != null) {
				tp(server, west[0] + 0.5, west[1] + 0.4, west[2] + 0.5, yawTo(west[0] + 0.5, west[2] + 0.5, cx + 0.5, cz + 0.5), 4);
			} else {
				tp(server, vx + 0.5, vy + 0.4, vz + 0.5, yaw, 4);
			}
			context.waitTicks(60);
			render(singleplayer);
			shot(context, "prostor_04_lake_sunset");
		}
	}

	/** Ждёт отрисовки чанков; если программный OpenGL не успел, снимок всё равно делается (с отметкой в логе). */
	private static void render(TestSingleplayerContext singleplayer) {
		try {
			singleplayer.getClientWorld().waitForChunksRender(RENDER_TIMEOUT);
		} catch (AssertionError e) {
			LOGGER.info("STATS| chunk render wait timed out, taking the screenshot anyway");
		}
	}

	/** Точка обзора у озера и замеры качества воды вокруг него: {x, y, z, центрX, центрZ, уровень воды}. */
	private static int[] lakeView(MinecraftServer server) {
		ServerWorld world = server.getOverworld();
		BlockPos at = server.getPlayerManager().getPlayerList().get(0).getBlockPos();
		ProstorCommands.Viewpoint view = ProstorCommands.find(world, ProstorBiomes.LAKE_DISTRICT, at);
		if (view == null || view.lake() == null) {
			return null;
		}
		Lakes.Lake lake = view.lake();
		LakeCheck.report(world, lake);
		LakeCheck.reportArea(world, lake.centerX(), lake.centerZ());
		StringBuilder reasons = new StringBuilder();
		for (int i = 0; i < Lakes.REASONS.length; i++) {
			reasons.append(Lakes.REASONS[i]).append('=').append(Lakes.STATS.get(i)).append(' ');
		}
		LOGGER.info("STATS| lake cells by outcome: {}", reasons);
		return new int[]{view.x(), view.y(), view.z(), lake.centerX(), lake.centerZ(), lake.waterY()};
	}

	/** Точка на восточном берегу того же озера: {x, y, z} или null. */
	private static int[] sunsetView(MinecraftServer server, int cx, int cz) {
		ServerWorld world = server.getOverworld();
		Lakes.Terrain terrain = new Lakes.Terrain(world.getSeed(), world.getChunkManager().getChunkGenerator(),
				world.getChunkManager().getNoiseConfig(), world);
		Lakes.Lake lake = Lakes.lake(terrain, Math.floorDiv(cx, Lakes.CELL), Math.floorDiv(cz, Lakes.CELL));
		if (lake == null) {
			return null;
		}
		ProstorCommands.Viewpoint view = ProstorCommands.lakeViewpoint(world, lake, 0.0);
		return view == null ? null : new int[]{view.x(), view.y(), view.z()};
	}

	private static double dist(double ax, double az, double bx, double bz) {
		return Math.max(0.001, Math.hypot(bx - ax, bz - az));
	}

	/** Угол поворота (yaw) Майнкрафта, чтобы смотреть из (x, z) на (tx, tz). */
	static float yawTo(double x, double z, double tx, double tz) {
		return (float) Math.toDegrees(Math.atan2(-(tx - x), tz - z));
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
