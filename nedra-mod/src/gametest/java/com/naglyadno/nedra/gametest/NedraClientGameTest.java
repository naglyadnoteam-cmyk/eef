package com.naglyadno.nedra.gametest;

import com.naglyadno.nedra.guide.GuideBook;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.Perspective;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Визуальная проверка мода в настоящем клиенте: строит "выставочный зал" из блоков мода на разной
 * глубине и снимает скриншоты HUD давления, текстур, брони, инвентаря и справочника.
 */
public class NedraClientGameTest implements FabricClientGameTest {

	private static final Logger LOGGER = LoggerFactory.getLogger("nedra-gametest");

	@Override
	public void runTest(ClientGameTestContext context) {
		startWatchdog();
		context.runOnClient(client -> {
			// размытие фона меню на программном OpenGL (CI без видеокарты) занимает секунды на кадр
			client.options.getMenuBackgroundBlurriness().setValue(0);
			client.options.getViewDistance().setValue(6);
			client.options.getSimulationDistance().setValue(5);
			// скриншоты - на русском, основном языке мода
			client.options.language = "ru_ru";
			client.getLanguageManager().setLanguage("ru_ru");
			client.reloadResources();
		});
		context.waitFor(client -> client.getOverlay() == null);
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			singleplayer.getClientWorld().waitForChunksRender();

			server.runCommand("difficulty peaceful");
			server.runCommand("gamemode survival @a");
			server.runCommand("time set noon");
			server.runCommand("effect give @a minecraft:resistance infinite 255 true");
			server.runCommand("effect give @a minecraft:saturation infinite 255 true");

			// ---- зал на глубине -190: стена с рудами и блоками мода, светильники в своде
			room(server, -192);
			String[] wall = {"nedra:lumenite_ore", "nedra:magnetite_ore", "nedra:echo_ore", "nedra:unstable_stone",
					"nedra:deepmoss", "nedra:lumenite_lamp", "nedra:current_vent"};
			for (int i = 0; i < wall.length; i++) {
				server.runCommand("setblock " + (i - 3) + " -189 -6 " + wall[i]);
				server.runCommand("setblock " + (i - 3) + " -190 -6 " + wall[i]);
			}
			server.runCommand("setblock 0 -192 -2 nedra:current_vent");
			server.runCommand("setblock -3 -192 -3 nedra:deepmoss");
			server.runCommand("setblock 3 -192 -3 nedra:deepmoss");
			server.runCommand("item replace entity @a armor.head with nedra:helmet_light");
			server.runCommand("item replace entity @a hotbar.0 with minecraft:air");
			server.runCommand("tp @a 0.5 -191 3.5 180 12");
			context.waitTicks(80);
			singleplayer.getClientWorld().waitForChunksRender();
			shot(context, "nedra_01_showroom_hud");

			// ---- три шлема на стойках
			server.runCommand("summon minecraft:armor_stand -1.5 -191 -1.5 {Rotation:[0f,0f],equipment:{head:{id:\"nedra:helmet_light\",count:1}}}");
			server.runCommand("summon minecraft:armor_stand 0.5 -191 -1.5 {Rotation:[0f,0f],equipment:{head:{id:\"nedra:helmet_reinforced\",count:1}}}");
			server.runCommand("summon minecraft:armor_stand 2.5 -191 -1.5 {Rotation:[0f,0f],equipment:{head:{id:\"nedra:helmet_deepsuit\",count:1}}}");
			server.runCommand("tp @a 0.5 -190 1.8 180 25");
			context.waitTicks(30);
			shot(context, "nedra_02_helmets");
			server.runCommand("kill @e[type=minecraft:armor_stand]");

			// ---- инвентарь со всеми предметами
			String[] items = {"nedra:geophone", "nedra:pressure_tablet 16", "nedra:lumenite_crystal 24", "nedra:raw_magnetite 12",
					"nedra:magnetite_ingot 9", "nedra:resonant_shard 3", "nedra:deepmoss_clump 20", "nedra:helmet_reinforced",
					"nedra:helmet_deepsuit", "nedra:lumenite_lamp 8", "nedra:lumenite_ore", "nedra:magnetite_ore",
					"nedra:echo_ore", "nedra:unstable_stone", "nedra:current_vent", "nedra:deepmoss", "minecraft:compass"};
			for (String item : items) {
				server.runCommand("give @a " + item);
			}
			context.waitTicks(10);
			context.runOnClient(client -> client.setScreen(new InventoryScreen(client.player)));
			context.waitTicks(10);
			shot(context, "nedra_03_inventory");

			// ---- справочник: титул и страница ярусов давления
			context.runOnClient(client -> {
				BookScreen.Contents contents = BookScreen.Contents.create(GuideBook.create());
				client.setScreen(new BookScreen(contents));
			});
			context.waitTicks(10);
			shot(context, "nedra_04_guide_title");
			context.runOnClient(client -> {
				if (client.currentScreen instanceof BookScreen book) {
					book.setPage(3);
				}
			});
			context.waitTicks(5);
			shot(context, "nedra_05_guide_tiers");
			context.runOnClient(client -> client.setScreen(null));

			// ---- самое дно без защиты: критическое давление, виньетка
			room(server, -346);
			server.runCommand("item replace entity @a armor.head with minecraft:air");
			server.runCommand("tp @a 0.5 -345 2.5 180 5");
			context.waitTicks(100);
			singleplayer.getClientWorld().waitForChunksRender();
			shot(context, "nedra_06_critical_depth");

			// ---- то же место в шлеме скафандра, вид от третьего лица
			server.runCommand("item replace entity @a armor.head with nedra:helmet_deepsuit");
			context.runOnClient(client -> client.options.setPerspective(Perspective.THIRD_PERSON_FRONT));
			context.waitTicks(80);
			shot(context, "nedra_07_deepsuit_third_person");
			context.runOnClient(client -> client.options.setPerspective(Perspective.FIRST_PERSON));
		}
	}

	/**
	 * Если загрузка мира зависла, в лог попадают стеки ключевых потоков (каждая строка с префиксом
	 * DUMP|, чтобы CI мог их отфильтровать) - так видно, где именно стоит сервер.
	 */
	private static void startWatchdog() {
		Thread watchdog = new Thread(() -> {
			try {
				Thread.sleep(40_000);
				for (int round = 0; round < 3; round++) {
					for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
						String name = entry.getKey().getName();
						if (!name.equals("Server thread") && !name.equals("Render thread") && !name.startsWith("Worker-Main")) {
							continue;
						}
						StringBuilder dump = new StringBuilder("DUMP| ").append(name).append(" [").append(entry.getKey().getState()).append("]");
						StackTraceElement[] frames = entry.getValue();
						for (int i = 0; i < Math.min(frames.length, 40); i++) {
							dump.append("\nDUMP|     ").append(frames[i]);
						}
						LOGGER.info(dump.toString());
					}
					Thread.sleep(8_000);
				}
			} catch (InterruptedException ignored) {
			}
		}, "nedra-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
	}

	/** Снимок без всплывающих уведомлений и чата, которые иначе закрывают HUD и книгу. */
	private static void shot(ClientGameTestContext context, String name) {
		context.runOnClient(client -> {
			client.getToastManager().clear();
			client.inGameHud.getChatHud().clear(false);
		});
		context.waitTicks(2);
		context.takeScreenshot(name);
	}

	/** Полая коробка из глубинного сланца 13x9x13 с полом на высоте floorY и светильниками в своде. */
	private static void room(TestServerContext server, int floorY) {
		int top = floorY + 8;
		server.runCommand("fill -6 " + floorY + " -6 6 " + top + " 6 minecraft:deepslate_tiles hollow");
		server.runCommand("fill -6 " + floorY + " -6 6 " + floorY + " 6 minecraft:polished_deepslate");
		int[][] lamps = {{0, 0}, {-4, -3}, {4, -3}, {-4, 3}, {4, 3}, {0, -4}};
		for (int[] lamp : lamps) {
			server.runCommand("setblock " + lamp[0] + " " + top + " " + lamp[1] + " nedra:lumenite_lamp");
		}
	}
}
