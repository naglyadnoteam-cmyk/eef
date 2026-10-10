package com.naglyadno.nedra.gametest;

import com.naglyadno.nedra.NedraMod;
import com.naglyadno.nedra.client.ClientPressureState;
import com.naglyadno.nedra.entity.ModEntities;
import com.naglyadno.nedra.entity.PrismGaleEntity;
import com.naglyadno.nedra.guide.GuideBook;
import com.naglyadno.nedra.block.ModBlocks;
import com.naglyadno.nedra.worldgen.deep.Citadels;
import com.naglyadno.nedra.worldgen.deep.FrozenCaverns;
import net.minecraft.block.Blocks;
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
			// выпавшие из них предметы и облачка частиц не должны попасть на следующий снимок
			server.runCommand("kill @e[type=minecraft:item]");
			context.waitTicks(60);

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

			// ---- страж цитадели среди кристаллической кладки и алых кристаллов
			// (сначала дожидаемся, пока исчезнут тела и облачка частиц прошлой сцены)
			server.runCommand("kill @e[type=!minecraft:player]");
			context.waitTicks(60);
			server.runCommand("kill @e[type=!minecraft:player]");
			server.runCommand("fill -4 -192 -5 4 -192 -1 nedra:crystal_tiles");
			server.runCommand("fill -5 -191 -6 5 -186 -6 nedra:crystal_bricks");
			server.runCommand("fill -1 -190 -6 1 -188 -6 nedra:chiseled_crystal_bricks");
			server.runCommand("setblock -3 -191 -5 nedra:scarlet_crystal_block");
			server.runCommand("setblock -3 -190 -5 nedra:scarlet_cluster[facing=up]");
			server.runCommand("setblock 3 -191 -5 nedra:scarlet_stone");
			server.runCommand("setblock 3 -190 -5 nedra:scarlet_cluster[facing=up]");
			server.runCommand("summon nedra:prism_gale 0.5 -191 -2.0 {NoAI:1b,PersistenceRequired:1b,Rotation:[0f,0f]}");
			server.runCommand("tp @a 2.5 -191 2.0 150 -4");
			// при слабом свете Minecraft красит всё в тёплый оранжевый - для честных цветов даём ночное зрение
			server.runCommand("effect give @a minecraft:night_vision 30 0 true");
			context.waitTicks(40);
			int showroomGales = server.computeOnServer(s -> count(s, ModEntities.PRISM_GALE));
			LOGGER.info("STATS| showroom prism gales: {}", showroomGales);
			shot(context, "nedra_19_prism_gale");
			server.runCommand("effect clear @a minecraft:night_vision");
			server.runCommand("kill @e[type=nedra:prism_gale]");
			spawnerCheck(context, server);
			server.runCommand("kill @e[type=minecraft:item]");
			server.runCommand("difficulty peaceful");

			// ---- инвентарь со всеми предметами
			String[] items = {"nedra:geophone", "nedra:pressure_tablet 16", "nedra:lumenite_crystal 24", "nedra:raw_magnetite 12",
					"nedra:magnetite_ingot 9", "nedra:resonant_shard 3", "nedra:deepmoss_clump 20", "nedra:helmet_reinforced",
					"nedra:helmet_deepsuit", "nedra:lumenite_lamp 8", "nedra:lumenite_ore", "nedra:magnetite_ore",
					"nedra:echo_ore", "nedra:unstable_stone", "nedra:current_vent", "nedra:deepmoss", "minecraft:compass",
					"nedra:rust_brute_spawn_egg 4", "nedra:deep_vine 16", "nedra:deep_fern 8", "nedra:glowcap 8",
					"nedra:overgrown_zombie_spawn_egg", "nedra:overgrown_skeleton_spawn_egg", "nedra:overgrown_creeper_spawn_egg",
					"nedra:prism_gale_spawn_egg", "nedra:scarlet_shard 12", "nedra:scarlet_cluster 4", "nedra:crystal_bricks 32"};
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
			waterfall(context, singleplayer, seed);
			BlockPos lush = DeepLocator.lush(seed, 0, 0);
			visit(context, singleplayer, lush, lush == null ? 0 : DeepLocator.bestYaw(seed, lush), 5, "nedra_21_lush_pocket");
			BlockPos scarlet = DeepLocator.layer(seed, DeepTerrain.Layer.SCARLET, 0, 0);
			visit(context, singleplayer, scarlet, scarlet == null ? 0 : DeepLocator.bestYaw(seed, scarlet), 5, "nedra_22_scarlet_grottoes");
			citadel(context, singleplayer, seed);
			frozenCaverns(context, singleplayer);
		}
	}

	/** Замёрзшая пещера: общий вид, лагерь экспедиции, перепись льда и естественный спавн её мобов. */
	private static void frozenCaverns(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		TestServerContext server = singleplayer.getServer();
		int[] info = server.computeOnServer(s -> {
			FrozenCaverns.Site site = FrozenCaverns.nearest(s.getOverworld(), 0, 0, 8);
			if (site == null) {
				return null;
			}
			BlockPos view = FrozenCaverns.viewPoint(s.getOverworld(), site);
			FrozenCaverns.Camp camp = site.camp();
			return new int[]{site.cx, site.floorY, site.cz, view.getX(), view.getY(), view.getZ(), site.maxHeight,
					camp == null ? 0 : 1, camp == null ? 0 : camp.x(), camp == null ? 0 : camp.y(), camp == null ? 0 : camp.z(),
					camp == null ? 0 : camp.fx(), camp == null ? 0 : camp.fz(), site.tunnels().length, site.falls().length};
		});
		if (info == null) {
			LOGGER.info("STATS| frozen cavern: not found");
			return;
		}
		LOGGER.info("STATS| frozen cavern at {} {} {} (dome {}, tunnels {}, falls {}, camp {})", info[0], info[1], info[2],
				info[6], info[13], info[14], info[7] == 1);
		float yaw = (float) Math.toDegrees(Math.atan2(-(info[0] - info[3]), info[2] - info[5]));
		server.runCommand(String.format(Locale.ROOT, "tp @a %d.5 %d %d.5 %.1f -8", info[3], info[4], info[5], yaw));
		context.waitTicks(160);
		singleplayer.getClientWorld().waitForChunksRender();
		context.waitTicks(40);
		shot(context, "nedra_25_frozen_caverns");
		// без ночного зрения - как пещеру видит игрок: голубой свет кристаллов и темнота между ними
		server.runCommand("effect clear @a minecraft:night_vision");
		context.waitTicks(40);
		shot(context, "nedra_28_frozen_glow");
		server.runCommand("effect give @a minecraft:night_vision infinite 0 true");
		String census = server.computeOnServer(s -> {
			ServerWorld world = s.getOverworld();
			Map<String, Integer> counts = new java.util.TreeMap<>();
			BlockPos.Mutable p = new BlockPos.Mutable();
			for (int x = info[0] - 70; x <= info[0] + 70; x++) {
				for (int z = info[2] - 70; z <= info[2] + 70; z++) {
					if (!world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) {
						continue;
					}
					for (int y = info[1] - 10; y <= info[1] + info[6] + 6; y++) {
						var state = world.getBlockState(p.set(x, y, z));
						for (var block : new net.minecraft.block.Block[]{Blocks.ICE, Blocks.PACKED_ICE, Blocks.BLUE_ICE, Blocks.SNOW_BLOCK,
								Blocks.SNOW, Blocks.POWDER_SNOW, Blocks.WATER, Blocks.LAVA, Blocks.SEA_LANTERN, Blocks.CHEST,
								ModBlocks.FROST_CRYSTAL, ModBlocks.ICICLE, ModBlocks.FROST_LEAVES}) {
							if (state.isOf(block)) {
								counts.merge(net.minecraft.registry.Registries.BLOCK.getId(block).getPath(), 1, Integer::sum);
							}
						}
					}
				}
			}
			BlockPos view = new BlockPos(info[3], info[4], info[5]);
			String biome = world.getBiome(view).getKey().map(k -> k.getValue().toString()).orElse("?");
			return counts + " biome at view: " + biome;
		});
		LOGGER.info("STATS| frozen cavern census (loaded part): {}", census);
		if (info[7] == 1) {
			int fx = info[11];
			int fz = info[12];
			int x = info[8] + fx * 6;
			int z = info[10] + fz * 6;
			float campYaw = (float) Math.toDegrees(Math.atan2(fx, -fz));
			server.runCommand(String.format(Locale.ROOT, "tp @a %d.5 %d %d.5 %.1f 10", x, info[9] + 1, z, campYaw));
			context.waitTicks(80);
			singleplayer.getClientWorld().waitForChunksRender();
			context.waitTicks(20);
			shot(context, "nedra_26_frozen_camp");
		}
		server.runCommand(String.format(Locale.ROOT, "tp @a %d.5 %d %d.5", info[3], info[4], info[5]));
		wildSpawns(context, singleplayer, "nedra_27_frostbitten_wild", ModEntities.FROSTBITTEN, EntityType.STRAY);
	}

	/**
	 * Спавнер хрустальных вихрей: за первые 5 секунд должен выпустить ровно двоих, а следующие
	 * 15 секунд - никого (задержка около минуты). Заодно здоровье стража.
	 */
	private static void spawnerCheck(ClientGameTestContext context, TestServerContext server) {
		context.waitTicks(20);
		server.runCommand("setblock 0 -191 -3 minecraft:spawner{SpawnData:{entity:{id:\"nedra:prism_gale\"}}}");
		context.waitTicks(100);
		int first = server.computeOnServer(s -> count(s, ModEntities.PRISM_GALE));
		double health = server.computeOnServer(s -> {
			for (Entity e : s.getOverworld().iterateEntities()) {
				if (e instanceof PrismGaleEntity gale) {
					return (double) gale.getMaxHealth();
				}
			}
			return -1.0;
		});
		context.waitTicks(300);
		int later = server.computeOnServer(s -> count(s, ModEntities.PRISM_GALE));
		LOGGER.info("STATS| prism gale spawner: {} after 5 s, {} after 20 s; max health {}", first, later, health);
		server.runCommand("setblock 0 -191 -3 minecraft:air");
		server.runCommand("kill @e[type=nedra:prism_gale]");
	}

	/** Водопад подземной реки: снимок и замер течения - насколько игрока сносит за 4 секунды. */
	private static void waterfall(ClientGameTestContext context, TestSingleplayerContext singleplayer, long seed) {
		DeepLocator.View view = DeepLocator.waterfall(seed, 1, 0, 0);
		if (view == null) {
			view = DeepLocator.waterfall(seed, 2, 0, 0);
		}
		if (view == null) {
			LOGGER.info("STATS| waterfall: not found");
			return;
		}
		TestServerContext server = singleplayer.getServer();
		BlockPos pos = view.pos();
		LOGGER.info("STATS| waterfall view: {} {} {} yaw {}", pos.getX(), pos.getY(), pos.getZ(), view.yaw());
		server.runCommand(String.format(Locale.ROOT, "tp @a %d.5 %d %d.5 %.1f 8", pos.getX(), pos.getY(), pos.getZ(), view.yaw()));
		// водопады начинают течь после загрузки чанков - даём воде время сбежать по ступени
		context.waitTicks(200);
		singleplayer.getClientWorld().waitForChunksRender();
		context.waitTicks(20);
		shot(context, "nedra_20_waterfall");
		// течение: ставим игрока в воду и смотрим, куда его снесёт без управления
		server.runCommand(String.format(Locale.ROOT, "tp @a %d.5 %d %d.5", pos.getX(), pos.getY() - 1, pos.getZ()));
		context.waitTicks(5);
		double[] before = server.computeOnServer(s -> {
			var p = s.getPlayerManager().getPlayerList().get(0);
			return new double[]{p.getX(), p.getZ()};
		});
		context.waitTicks(80);
		double[] after = server.computeOnServer(s -> {
			var p = s.getPlayerManager().getPlayerList().get(0);
			return new double[]{p.getX(), p.getZ(), p.isTouchingWater() ? 1 : 0};
		});
		double[] flow = DeepTerrain.of(seed).riverFlow(1, pos.getX(), pos.getZ());
		double along = flow == null ? 0 : (after[0] - before[0]) * flow[0] + (after[1] - before[1]) * flow[1];
		LOGGER.info(String.format(Locale.ROOT, "STATS| river current: moved %.2f blocks downstream in 80 ticks (dx %.2f, dz %.2f, in water %s)",
				along, after[0] - before[0], after[1] - before[1], after[2] > 0));
	}

	/** Хрустальная цитадель: Сердце и вход, сундуки, спавнеры и стражи внутри. */
	private static void citadel(ClientGameTestContext context, TestSingleplayerContext singleplayer, long seed) {
		Citadels.Site site = DeepLocator.citadel(seed, 0, 0);
		if (site == null) {
			LOGGER.info("STATS| citadel: not found");
			return;
		}
		TestServerContext server = singleplayer.getServer();
		int cx = site.centerX();
		int cz = site.centerZ();
		int y0 = site.floorY();
		LOGGER.info("STATS| citadel at {} {} {} (entrance side {})", cx, y0, cz, site.entranceSide());
		// на мирной сложности стражи исчезают - для переписи включаем лёгкую
		server.runCommand("difficulty easy");
		// с края Сердца, между колоннами, на центральный кристалл
		server.runCommand(String.format(Locale.ROOT, "tp @a %d.5 %d %d.5 110 -12", cx + 8, y0, cz + 3));
		// стражи замирают для снимка, иначе их заряды ветра отбрасывают камеру
		for (int i = 0; i < 15; i++) {
			context.waitTicks(10);
			server.runCommand("execute as @e[type=nedra:prism_gale] run data merge entity @s {NoAI:1b}");
		}
		server.runCommand(String.format(Locale.ROOT, "tp @a %d.5 %d %d.5 110 -12", cx + 8, y0, cz + 3));
		singleplayer.getClientWorld().waitForChunksRender();
		context.waitTicks(30);
		shot(context, "nedra_23_citadel_heart");
		String census = server.computeOnServer(s -> {
			var world = s.getOverworld();
			int chests = 0;
			int spawners = 0;
			int bricks = 0;
			BlockPos.Mutable p = new BlockPos.Mutable();
			for (int x = site.x0(); x <= site.maxX(); x++) {
				for (int z = site.z0(); z <= site.maxZ(); z++) {
					if (!world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) {
						continue;
					}
					for (int y = y0 - 2; y <= y0 + Citadels.HEIGHT + Citadels.HEART_EXTRA + 1; y++) {
						var state = world.getBlockState(p.set(x, y, z));
						if (state.isOf(Blocks.CHEST)) {
							chests++;
						} else if (state.isOf(Blocks.SPAWNER)) {
							spawners++;
						} else if (state.isOf(ModBlocks.CRYSTAL_BRICKS)) {
							bricks++;
						}
					}
				}
			}
			int guards = count(s, ModEntities.PRISM_GALE);
			return "chests=" + chests + " spawners=" + spawners + " crystal_bricks=" + bricks + " prism_gales=" + guards;
		});
		LOGGER.info("STATS| citadel census (loaded part): {}", census);
		int[] room = Citadels.entranceRoom(site);
		// встаём у входной стены зала и смотрим внутрь замка
		int rx = site.roomX(room[0]) + switch (site.entranceSide()) {
			case 1 -> 11;
			case 3 -> 1;
			default -> 6;
		};
		int rz = site.roomZ(room[1]) + switch (site.entranceSide()) {
			case 0 -> 1;
			case 2 -> 11;
			default -> 6;
		};
		float yaw = switch (site.entranceSide()) {
			case 0 -> 0.0F;
			case 1 -> 90.0F;
			case 2 -> 180.0F;
			default -> -90.0F;
		};
		server.runCommand(String.format(Locale.ROOT, "tp @a %d.5 %d %d.5 %.1f 0", rx, y0, rz, yaw));
		context.waitTicks(60);
		singleplayer.getClientWorld().waitForChunksRender();
		context.waitTicks(20);
		shot(context, "nedra_24_citadel_rooms");
		server.runCommand("difficulty peaceful");
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
		// освобождаем лимит враждебных мобов, занятый мобами прошлых сцен, иначе новые не появятся
		server.runCommand("kill @e[type=!minecraft:player]");
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
