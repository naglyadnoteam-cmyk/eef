package com.naglyadno.nedra.gametest;

import com.naglyadno.nedra.NedraMod;
import com.naglyadno.nedra.client.ClientPressureState;
import com.naglyadno.nedra.entity.ModEntities;
import com.naglyadno.nedra.guide.GuideBook;
import com.naglyadno.nedra.pressure.PressureManager;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import com.naglyadno.nedra.worldgen.deep.DeepLocator;
import com.naglyadno.nedra.worldgen.deep.DeepTerrain;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;

/**
 * Визуальная проверка мода в настоящем клиенте: строит "выставочный зал" из блоков мода на разной
 * глубине и снимает скриншоты HUD давления, текстур, брони, инвентаря и справочника.
 */
public class NedraClientGameTest implements FabricClientGameTest {

	private static final Logger LOGGER = LoggerFactory.getLogger("nedra-gametest");

	@Override
	public void runTest(ClientGameTestContext context) {
		// startWatchdog(); - включить при отладке зависаний загрузки мира
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

			pressureCheck(context, singleplayer);

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

			// ---- ржавый громила рядом с обычным зомби: видно цвет и разницу в росте
			server.runCommand("difficulty easy");
			server.runCommand("summon nedra:rust_brute -1.5 -191 -2.5 {NoAI:1b,PersistenceRequired:1b,Rotation:[20f,0f]}");
			server.runCommand("summon minecraft:zombie 2.5 -191 -2.5 {NoAI:1b,PersistenceRequired:1b,IsBaby:0b,Rotation:[-20f,0f]}");
			server.runCommand("tp @a 0.5 -191 3.5 180 -6");
			context.waitTicks(30);
			shot(context, "nedra_09_rust_brute");
			server.runCommand("kill @e[type=nedra:rust_brute]");
			server.runCommand("kill @e[type=minecraft:zombie]");

			// ---- жители Заросших глубин и их растения: мох, папоротник, светошляпка, свисающая лиана
			server.runCommand("fill -4 -192 -5 4 -192 -1 minecraft:moss_block");
			server.runCommand("setblock -3 -191 -4 nedra:deep_fern");
			server.runCommand("setblock 3 -191 -4 nedra:deep_fern");
			server.runCommand("setblock -1 -191 -5 nedra:glowcap");
			server.runCommand("setblock 2 -191 -5 nedra:glowcap");
			server.runCommand("setblock -2 -185 -5 nedra:deep_vine[tip=false]");
			server.runCommand("setblock -2 -186 -5 nedra:deep_vine[tip=false]");
			server.runCommand("setblock -2 -187 -5 nedra:deep_vine[tip=true,bloom=true]");
			server.runCommand("setblock 3 -185 -5 nedra:deep_vine[tip=false]");
			server.runCommand("setblock 3 -186 -5 nedra:deep_vine[tip=true,bloom=false]");
			server.runCommand("summon nedra:overgrown_zombie -2.5 -191 -2.5 {NoAI:1b,PersistenceRequired:1b,IsBaby:0b,Rotation:[20f,0f]}");
			server.runCommand("summon nedra:overgrown_skeleton 0.5 -191 -2.5 {NoAI:1b,PersistenceRequired:1b,Rotation:[0f,0f]}");
			server.runCommand("summon nedra:overgrown_creeper 3.5 -191 -2.5 {NoAI:1b,PersistenceRequired:1b,Rotation:[-20f,0f]}");
			server.runCommand("tp @a 0.5 -191 3.5 180 -4");
			context.waitTicks(30);
			shot(context, "nedra_16_overgrown_mobs");
			for (String mob : new String[]{"overgrown_zombie", "overgrown_skeleton", "overgrown_creeper"}) {
				server.runCommand("kill @e[type=nedra:" + mob + "]");
			}
			server.runCommand("kill @e[type=minecraft:item]");
			server.runCommand("difficulty peaceful");

			// ---- инвентарь со всеми предметами
			String[] items = {"nedra:geophone", "nedra:pressure_tablet 16", "nedra:lumenite_crystal 24", "nedra:raw_magnetite 12",
					"nedra:magnetite_ingot 9", "nedra:resonant_shard 3", "nedra:deepmoss_clump 20", "nedra:helmet_reinforced",
					"nedra:helmet_deepsuit", "nedra:lumenite_lamp 8", "nedra:lumenite_ore", "nedra:magnetite_ore",
					"nedra:echo_ore", "nedra:unstable_stone", "nedra:current_vent", "nedra:deepmoss", "minecraft:compass",
					"nedra:rust_brute_spawn_egg 4", "nedra:deep_vine 16", "nedra:deep_fern 8", "nedra:glowcap 8",
					"nedra:overgrown_zombie_spawn_egg", "nedra:overgrown_skeleton_spawn_egg", "nedra:overgrown_creeper_spawn_egg"};
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

		naturalWorld(context);
	}

	/**
	 * Обычный (не плоский) мир: поверхность должна выглядеть ванильной, а недра - пещеры без
	 * лавовых морей и биомы по ярусам. Снимок поверхности, статистика генерации в лог (STATS|)
	 * и снимок настоящей глубинной пещеры.
	 */
	private static void naturalWorld(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder()
				.setUseConsistentSettings(false)
				.adjustSettings(creator -> creator.setSeed("nedra"))
				.create()) {
			TestServerContext server = singleplayer.getServer();
			singleplayer.getClientWorld().waitForChunksRender();
			server.runCommand("difficulty peaceful");
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("gamemode survival @a");
			server.runCommand("effect give @a minecraft:resistance infinite 255 true");
			server.runCommand("effect give @a minecraft:night_vision infinite 0 true");
			server.runCommand("effect give @a minecraft:slow_falling infinite 0 true");

			// вид на поверхность сверху: рельеф должен быть обычным
			server.runCommand("execute as @a at @s run tp @s ~ ~25 ~ 30 20");
			context.waitTicks(60);
			singleplayer.getClientWorld().waitForChunksRender();
			shot(context, "nedra_08_surface");

			server.computeOnServer(WorldStats::report);

			// места недр находим той же функцией, что и /nedra locate, и снимаем каждое
			long seed = server.computeOnServer(s -> s.getOverworld().getSeed());
			// в шлеме скафандра: на снимках биомов не нужна красная виньетка давления
			server.runCommand("item replace entity @a armor.head with nedra:helmet_deepsuit");
			visit(context, singleplayer, DeepLocator.settlement(seed, 0, 0), 45, 8, "nedra_10_settlement");
			BlockPos echo = DeepLocator.layer(seed, DeepTerrain.Layer.ECHO, 0, 0);
			BlockPos magnetic = DeepLocator.layer(seed, DeepTerrain.Layer.MAGNETIC, 0, 0);
			BlockPos crystal = DeepLocator.layer(seed, DeepTerrain.Layer.CRYSTAL, 0, 0);
			visit(context, singleplayer, echo, echo == null ? 0 : DeepLocator.bestYaw(seed, echo), 5, "nedra_11_echo_hollows");
			visit(context, singleplayer, magnetic, magnetic == null ? 0 : DeepLocator.bestYaw(seed, magnetic), -5, "nedra_12_magnetic_caverns");
			if (magnetic != null) {
				wildSpawns(context, singleplayer, "nedra_15_rust_brute_wild", ModEntities.RUST_BRUTE);
			}
			visit(context, singleplayer, crystal, crystal == null ? 0 : DeepLocator.bestYaw(seed, crystal), 5, "nedra_13_crystal_depths");
			BlockPos river = DeepLocator.river(seed, 1, 0, 0);
			visit(context, singleplayer, river == null ? null : river.up(), 0, 20, "nedra_14_river");
			BlockPos overgrown = DeepLocator.layer(seed, DeepTerrain.Layer.JUNGLE, 0, 0);
			visit(context, singleplayer, overgrown, overgrown == null ? 0 : DeepLocator.bestYaw(seed, overgrown), 5,
					"nedra_17_overgrown_depths");
			if (overgrown != null) {
				wildSpawns(context, singleplayer, "nedra_18_overgrown_wild",
						ModEntities.OVERGROWN_ZOMBIE, ModEntities.OVERGROWN_SKELETON, ModEntities.OVERGROWN_CREEPER);
			}
		}
	}

	/**
	 * Замеры давления на разных высотах (сервер и HUD клиента), защита шлемов, творческий режим и урон
	 * на дне без защиты. Всё пишется в лог строками STATS| pressure ...
	 */
	private static void pressureCheck(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		TestServerContext server = singleplayer.getServer();
		server.runCommand("item replace entity @a armor.head with minecraft:air");
		// плоский мир заканчивается землёй на Y -349, поэтому самая нижняя точка - -347 (в воздухе, не в блоке)
		int[] heights = {40, 0, -10, -36, -64, -107, -150, -195, -230, -265, -300, -325, -347};
		for (int y : heights) {
			pressureAt(context, server, y, "no helmet");
		}
		String[] helmets = {"nedra:helmet_light", "nedra:helmet_reinforced", "nedra:helmet_deepsuit"};
		for (String helmet : helmets) {
			server.runCommand("item replace entity @a armor.head with " + helmet);
			pressureAt(context, server, -300, helmet);
		}
		server.runCommand("item replace entity @a armor.head with minecraft:air");
		server.runCommand("gamemode creative @a");
		pressureAt(context, server, -300, "creative");
		server.runCommand("gamemode survival @a");

		// урон на дне без защиты: должен идти, но никогда не опускать здоровье ниже 1
		pressureAt(context, server, -347, "damage test");
		server.runCommand("effect clear @a minecraft:resistance");
		server.runCommand("effect clear @a minecraft:saturation");
		// в мирной сложности и на сытый желудок здоровье восстанавливается само - на время проверки отключаем
		server.runCommand("difficulty easy");
		server.runOnServer(s -> {
			ServerPlayerEntity player = s.getPlayerManager().getPlayerList().get(0);
			player.getHungerManager().setFoodLevel(10);
			player.getHungerManager().setSaturationLevel(0f);
		});
		for (int i = 1; i <= 8; i++) {
			context.waitTicks(40);
			float health = server.computeOnServer(s -> s.getPlayerManager().getPlayerList().get(0).getHealth());
			LOGGER.info("STATS| pressure damage after {} ticks: health {}", i * 40, health);
		}
		server.runCommand("effect give @a minecraft:resistance infinite 255 true");
		server.runCommand("effect give @a minecraft:saturation infinite 255 true");
		server.runCommand("effect give @a minecraft:instant_health 1 10 true");
		server.runCommand("difficulty peaceful");
		context.waitTicks(10);
		// страховка: если игрок всё же погиб, возрождаем его, чтобы экран смерти не попал на снимки
		context.runOnClient(client -> {
			if (client.player != null && client.player.isDead()) {
				client.player.requestRespawn();
			}
		});
		context.waitTicks(20);
	}

	private static void pressureAt(ClientGameTestContext context, TestServerContext server, int y, String label) {
		server.runCommand("setblock 0 " + (y - 1) + " 0 minecraft:polished_deepslate");
		server.runCommand("tp @a 0.5 " + y + " 0.5");
		context.waitTicks(30);
		String serverSide = server.computeOnServer(s -> {
			ServerPlayerEntity player = s.getPlayerManager().getPlayerList().get(0);
			PressureManager.Reading r = NedraMod.pressureManager().read(player);
			double mining = player.getAttributeValue(EntityAttributes.BLOCK_BREAK_SPEED);
			return String.format(Locale.ROOT, "Y=%d raw=%.1f%% effective=%.1f%% protection=%d%% tier=%s mining=x%.2f",
					player.getBlockY(), r.raw(), r.effective(), r.protection(), r.tier(), mining);
		});
		String clientSide = context.computeOnClient(client -> String.format(Locale.ROOT,
				"hud visible=%.2f shown=%.1f%% label tier=%d effect tier=%d exempt=%s",
				ClientPressureState.visibility(), ClientPressureState.effective(), ClientPressureState.displayTier(),
				ClientPressureState.tier(), ClientPressureState.exempt()));
		LOGGER.info("STATS| pressure [{}] {} | {}", label, serverSide, clientSide);
	}

	/**
	 * Естественный спавн: на нормальной сложности ждём у биома, считаем его мобов в мире и снимаем
	 * ближайшего, который появился сам (без /summon).
	 */
	private static void wildSpawns(ClientGameTestContext context, TestSingleplayerContext singleplayer, String shotName,
			EntityType<?>... types) {
		TestServerContext server = singleplayer.getServer();
		server.runCommand("difficulty normal");
		for (int round = 1; round <= 3; round++) {
			context.waitTicks(300);
			int total = 0;
			StringBuilder line = new StringBuilder();
			for (EntityType<?> type : types) {
				int n = server.computeOnServer(s -> count(s, type));
				total += n;
				line.append(EntityType.getId(type).getPath()).append('=').append(n).append(' ');
			}
			int zombies = server.computeOnServer(s -> count(s, EntityType.ZOMBIE));
			LOGGER.info("STATS| natural spawn after {} ticks: {}zombie={}", round * 300, line, zombies);
			if (total > 0) {
				break;
			}
		}
		Vec3d view = server.computeOnServer(s -> viewMob(s, types));
		if (view == null) {
			LOGGER.info("STATS| {}: no mob with an open view", shotName);
			server.runCommand("difficulty peaceful");
			return;
		}
		LOGGER.info("STATS| {} view from {}", shotName, view);
		server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f facing entity @e[tag=nedra_view,limit=1] eyes",
				view.x, view.y, view.z));
		context.waitTicks(60);
		singleplayer.getClientWorld().waitForChunksRender();
		context.waitTicks(20);
		shot(context, shotName);
		server.runCommand("tag @e[tag=nedra_view] remove nedra_view");
		server.runCommand("difficulty peaceful");
	}

	/**
	 * Ищет моба одного из типов, к которому можно встать в 3-4 блоках по открытому воздуху, замораживает
	 * его (тег nedra_view) и возвращает точку для камеры.
	 */
	private static Vec3d viewMob(MinecraftServer server, EntityType<?>[] types) {
		ServerWorld world = server.getOverworld();
		for (Entity entity : world.iterateEntities()) {
			if (!(entity instanceof MobEntity mob) || !java.util.Arrays.asList(types).contains(entity.getType())) {
				continue;
			}
			BlockPos base = mob.getBlockPos();
			for (int r = 4; r >= 3; r--) {
				for (int i = 0; i < 8; i++) {
					double angle = Math.toRadians(i * 45.0);
					int dx = (int) Math.round(Math.cos(angle) * r);
					int dz = (int) Math.round(Math.sin(angle) * r);
					boolean open = true;
					for (int step = 1; step <= r && open; step++) {
						BlockPos p = base.add(Math.round((float) dx * step / r), 0, Math.round((float) dz * step / r));
						open = world.getBlockState(p).isAir() && world.getBlockState(p.up()).isAir();
					}
					BlockPos feet = base.add(dx, 0, dz);
					if (open && !world.getBlockState(feet.down()).isAir()) {
						mob.setAiDisabled(true);
						mob.setPersistent();
						mob.addCommandTag("nedra_view");
						return new Vec3d(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
					}
				}
			}
		}
		return null;
	}

	private static int count(MinecraftServer server, EntityType<?> type) {
		int n = 0;
		for (Entity entity : server.getOverworld().iterateEntities()) {
			if (entity.getType() == type) {
				n++;
			}
		}
		return n;
	}

	private static void visit(ClientGameTestContext context, TestSingleplayerContext singleplayer, BlockPos pos,
			float yaw, float pitch, String name) {
		if (pos == null) {
			LOGGER.info("STATS| {}: место не найдено", name);
			return;
		}
		LOGGER.info("STATS| {}: {} {} {}", name, pos.getX(), pos.getY(), pos.getZ());
		singleplayer.getServer().runCommand("tp @a " + pos.getX() + ".5 " + pos.getY() + " " + pos.getZ() + ".5 " + yaw + " " + pitch);
		context.waitTicks(120);
		singleplayer.getClientWorld().waitForChunksRender();
		context.waitTicks(40);
		shot(context, name);
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
