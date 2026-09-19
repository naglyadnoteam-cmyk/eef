package com.naglyadno.fatecards.cards;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Полный набор карт судьбы. Основано на присланном списке из 100 карточек;
 * несколько карт, которые нельзя реализовать надёжно без взлома клиента
 * (перевёрнутое управление, точная визуальная копия другого игрока, обмен
 * целыми областями мира), заменены близкими по духу, но устойчивыми
 * аналогами — это отмечено в комментариях у соответствующих карт.
 */
public final class CardRegistry {

	private static final List<Card> CARDS = new ArrayList<>();
	private static final Random EFFECT_RANDOM = new Random();

	private CardRegistry() {
	}

	private static void add(String id, String name, String desc, Category cat, int weight, CardEffect effect) {
		CARDS.add(new Card(id, name, desc, cat, weight, effect));
	}

	public static Card randomCard(Random random) {
		int total = 0;
		for (Card c : CARDS) {
			total += c.weight();
		}
		int roll = random.nextInt(total);
		int acc = 0;
		for (Card c : CARDS) {
			acc += c.weight();
			if (roll < acc) {
				return c;
			}
		}
		return CARDS.get(CARDS.size() - 1);
	}

	public static Card randomCardExcluding(Random random, Card exclude) {
		Card c;
		int attempts = 0;
		do {
			c = randomCard(random);
			attempts++;
		} while (c == exclude && attempts < 20);
		return c;
	}

	public static List<Card> all() {
		return CARDS;
	}

	// ------------------------------------------------------------------
	// Общие помощники
	// ------------------------------------------------------------------

	private static void potion(ServerPlayerEntity player, RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect, int ticks, int amplifier) {
		player.addStatusEffect(new StatusEffectInstance(effect, ticks, amplifier, false, true));
	}

	private static void tempAttribute(CardContext ctx, RegistryEntry<EntityAttribute> attribute, double amount,
			EntityAttributeModifier.Operation op, int ticks) {
		ServerPlayerEntity player = ctx.target();
		EntityAttributeInstance instance = player.getAttributeInstance(attribute);
		if (instance == null) {
			return;
		}
		Identifier id = Identifier.of("fatecards", "temp_" + UUID.randomUUID());
		instance.addTemporaryModifier(new EntityAttributeModifier(id, amount, op));
		ctx.game().schedule(ticks, () -> {
			EntityAttributeInstance current = player.getAttributeInstance(attribute);
			if (current != null) {
				current.removeModifier(id);
			}
		});
	}

	private static BlockPos feet(CardContext ctx) {
		return ctx.target().getBlockPos();
	}

	/**
	 * Ищет ближайший биом {@code biomeKey} в измерении {@code dimension} (до 6400 блоков)
	 * и телепортирует туда игрока; если не нашлось — переносит в случайное далёкое место,
	 * чтобы карта никогда не "не сработала" впустую.
	 */
	private static void teleportToBiome(CardContext ctx, net.minecraft.registry.RegistryKey<World> dimension,
			net.minecraft.registry.RegistryKey<net.minecraft.world.biome.Biome> biomeKey, String biomeLabel) {
		ServerPlayerEntity player = ctx.target();
		ServerWorld world = ctx.server().getWorld(dimension);
		if (world == null) {
			return;
		}
		BlockPos searchOrigin = world == player.getEntityWorld() ? player.getBlockPos() : BlockPos.ORIGIN;
		com.mojang.datafixers.util.Pair<BlockPos, RegistryEntry<net.minecraft.world.biome.Biome>> result;
		try {
			result = world.locateBiome(entry -> entry.matchesKey(biomeKey), searchOrigin, 6400, 32, 64);
		} catch (Exception e) {
			result = null;
		}
		if (result == null) {
			player.sendMessage(net.minecraft.text.Text.literal(
					"Биом \"" + biomeLabel + "\" не нашёлся поблизости — переносим в случайное далёкое место.")
					.formatted(net.minecraft.util.Formatting.GRAY), false);
			double angle = EFFECT_RANDOM.nextDouble() * Math.PI * 2;
			CardEffects.teleportSafe(player, world, searchOrigin.getX() + Math.cos(angle) * 400,
					searchOrigin.getZ() + Math.sin(angle) * 400);
			return;
		}
		BlockPos pos = result.getFirst();
		CardEffects.teleportSafe(player, world, pos.getX(), pos.getZ());
	}

	// ==================================================================
	// 1-15. Характеристики
	// ==================================================================

	static {
		add("stat_extra_heart", "Дополнительное сердце", "+5 сердец на 60 секунд.", Category.STATS, 10, ctx -> {
			potion(ctx.target(), StatusEffects.HEALTH_BOOST, 60 * 20, 4);
			ctx.target().setHealth(ctx.target().getMaxHealth());
		});

		add("stat_glass_heart", "Стеклянное сердце", "Максимальное здоровье уменьшается вдвое на 45 секунд.",
				Category.STATS, 10, ctx -> tempAttribute(ctx, EntityAttributes.MAX_HEALTH, -0.5,
						EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, 45 * 20));

		add("stat_adrenaline", "Адреналин", "Скорость передвижения увеличена на 20 секунд.", Category.STATS, 12,
				ctx -> potion(ctx.target(), StatusEffects.SPEED, 20 * 20, 3));

		add("stat_moon_step", "Лунный шаг", "Очень высокий прыжок на 10 секунд.", Category.STATS, 10,
				ctx -> potion(ctx.target(), StatusEffects.JUMP_BOOST, 10 * 20, 5));

		add("stat_stone_legs", "Каменные ноги", "Почти не может прыгать 15 секунд.", Category.STATS, 10, ctx -> {
			potion(ctx.target(), StatusEffects.JUMP_BOOST, 15 * 20, 200);
			potion(ctx.target(), StatusEffects.SLOWNESS, 15 * 20, 1);
		});

		add("stat_regen", "Регенерация", "Полностью восстанавливает здоровье.", Category.STATS, 10,
				ctx -> ctx.target().setHealth(ctx.target().getMaxHealth()));

		add("stat_hungry_day", "Голодный день", "Голод уменьшается намного быстрее в течение минуты.",
				Category.STATS, 9, ctx -> potion(ctx.target(), StatusEffects.HUNGER, 60 * 20, 2));

		add("stat_immortal", "Бессмертие", "Не может умереть 10 секунд.", Category.STATS, 7, ctx -> {
			ServerPlayerEntity player = ctx.target();
			boolean wasInvulnerable = player.isInvulnerable();
			player.setInvulnerable(true);
			ctx.game().schedule(10 * 20, () -> player.setInvulnerable(wasInvulnerable));
		});

		add("stat_one_heart", "Одно сердце", "Здоровье мгновенно падает до одного сердца.", Category.STATS, 8,
				ctx -> ctx.target().setHealth(2.0f));

		add("stat_vampire", "Вампир", "Сила удара и лёгкое восстановление здоровья на 20 секунд.", Category.STATS, 9,
				ctx -> {
					potion(ctx.target(), StatusEffects.STRENGTH, 20 * 20, 1);
					potion(ctx.target(), StatusEffects.REGENERATION, 20 * 20, 0);
				});

		add("stat_blind_miner", "Слепой шахтёр", "Зрение сильно ухудшается на 15 секунд.", Category.STATS, 10,
				ctx -> potion(ctx.target(), StatusEffects.BLINDNESS, 15 * 20, 0));

		add("stat_super_hearing", "Суперслух", "Рядом громко звучат звуки существ 30 секунд.", Category.STATS, 6,
				ctx -> ctx.game().scheduleRepeating(20, 6, i ->
						ctx.target().playSoundToPlayer(net.minecraft.sound.SoundEvents.ENTITY_ZOMBIE_AMBIENT,
								net.minecraft.sound.SoundCategory.HOSTILE, 3f, 0.7f)));

		add("stat_water_person", "Водяной человек", "Дыхание под водой и скорость плавания на 60 секунд.",
				Category.STATS, 10, ctx -> {
					potion(ctx.target(), StatusEffects.WATER_BREATHING, 60 * 20, 0);
					potion(ctx.target(), StatusEffects.DOLPHINS_GRACE, 60 * 20, 0);
				});

		add("stat_pause", "Пауза", "Действия и передвижение замедлены на 20 секунд.", Category.STATS, 9, ctx -> {
			potion(ctx.target(), StatusEffects.SLOWNESS, 20 * 20, 1);
			potion(ctx.target(), StatusEffects.MINING_FATIGUE, 20 * 20, 1);
		});

		add("stat_berserk", "Берсерк", "Огромная сила удара на 15 секунд.", Category.STATS, 8,
				ctx -> potion(ctx.target(), StatusEffects.STRENGTH, 15 * 20, 2));
	}

	// ==================================================================
	// 16-30. Гравитация и физика
	// ==================================================================

	static {
		add("phys_moon_gravity", "Лунная гравитация", "Прыжок примерно втрое выше на 15 секунд.", Category.PHYSICS, 10,
				ctx -> potion(ctx.target(), StatusEffects.JUMP_BOOST, 15 * 20, 3));

		add("phys_inverted_gravity", "Перевёрнутая гравитация", "Игрока периодически подбрасывает вверх.",
				Category.PHYSICS, 8, ctx -> ctx.game().scheduleRepeating(60, 5, i ->
						CardEffects.impulseUp(ctx.target(), 0.6)));

		add("phys_rubber_man", "Резиновый человек", "Падение не наносит урон 30 секунд.", Category.PHYSICS, 9,
				ctx -> potion(ctx.target(), StatusEffects.SLOW_FALLING, 30 * 20, 0));

		add("phys_slippery_world", "Скользкий мир", "Лёд под ногами на 20 секунд.", Category.PHYSICS, 9, ctx -> {
			var snapshot = CardEffects.areaReplaceSnapshot(ctx.world(), feet(ctx), 4, 1, Blocks.PACKED_ICE.getDefaultState());
			ctx.game().schedule(20 * 20, () -> CardEffects.restoreSnapshot(ctx.world(), snapshot));
		});

		add("phys_magnet", "Магнит", "Предметы вокруг летят к игроку 15 секунд.", Category.PHYSICS, 8, ctx -> {
			ServerPlayerEntity player = ctx.target();
			ServerWorld world = ctx.world();
			ctx.game().scheduleRepeating(5, 60, i -> {
				for (var item : world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,
						player.getBoundingBox().expand(8), e -> true)) {
					var dir = player.getEntityPos().subtract(item.getEntityPos());
					if (dir.length() > 0.5) {
						item.setVelocity(dir.normalize().multiply(0.3));
						item.velocityDirty = true;
					}
				}
			});
		});

		add("phys_antigravity", "Антигравитация", "Медленно поднимается вверх 8 секунд.", Category.PHYSICS, 8,
				ctx -> ctx.game().scheduleRepeating(10, 16, i -> CardEffects.impulseUp(ctx.target(), 0.15)));

		add("phys_stone_body", "Камень", "Прыжок почти отсутствует на 15 секунд.", Category.PHYSICS, 8,
				ctx -> potion(ctx.target(), StatusEffects.JUMP_BOOST, 15 * 20, 200));

		add("phys_leap_of_fate", "Прыжок судьбы", "Мощный рывок вперёд и вверх прямо сейчас.", Category.PHYSICS, 8,
				ctx -> {
					float yaw = (float) Math.toRadians(ctx.target().getYaw());
					CardEffects.impulse(ctx.target(), -Math.sin(yaw) * 1.6, 0.8, Math.cos(yaw) * 1.6);
				});

		add("phys_hop_hop", "Прыг-скок", "Игрок сам подпрыгивает каждые 3 секунды на протяжении 18 секунд.",
				Category.PHYSICS, 8, ctx -> ctx.game().scheduleRepeating(60, 6, i -> CardEffects.impulseUp(ctx.target(), 0.5)));

		add("phys_turbo_run", "Турбо-бег", "Очень быстрый бег на 15 секунд.", Category.PHYSICS, 9,
				ctx -> potion(ctx.target(), StatusEffects.SPEED, 15 * 20, 5));

		add("phys_invisible_push", "Невидимый толчок", "Случайные лёгкие толчки в течение 15 секунд.",
				Category.PHYSICS, 8, ctx -> ctx.game().scheduleRepeating(40, 8, i -> {
					double dx = (EFFECT_RANDOM.nextDouble() - 0.5) * 1.2;
					double dz = (EFFECT_RANDOM.nextDouble() - 0.5) * 1.2;
					CardEffects.impulse(ctx.target(), dx, 0.15, dz);
				}));

		add("phys_spring_ground", "Пружинная земля", "20 секунд каждое приземление подбрасывает вверх.",
				Category.PHYSICS, 7, ctx -> {
					ServerPlayerEntity player = ctx.target();
					boolean[] wasOnGround = {player.isOnGround()};
					ctx.game().scheduleRepeating(2, 200, i -> {
						boolean onGround = player.isOnGround();
						if (onGround && !wasOnGround[0]) {
							CardEffects.impulseUp(player, 0.7);
						}
						wasOnGround[0] = onGround;
					});
				});

		add("phys_sky_calls", "Небо зовёт", "Телепортирует на 20 блоков вверх с безопасным спуском.",
				Category.PHYSICS, 8, ctx -> {
					CardEffects.teleportUp(ctx.target(), 20);
					potion(ctx.target(), StatusEffects.SLOW_FALLING, 15 * 20, 0);
				});
	}

	// ==================================================================
	// 31-45. Изменение мира
	// ==================================================================

	static {
		add("world_stone_area", "Каменный чанк", "Область вокруг превращается в камень.", Category.WORLD, 9,
				ctx -> CardEffects.areaReplace(ctx.world(), feet(ctx), 5, 2, Blocks.STONE.getDefaultState(), false));

		add("world_water_area", "Область воды", "Область вокруг заполняется водой.", Category.WORLD, 8,
				ctx -> CardEffects.areaReplace(ctx.world(), feet(ctx), 4, 1, Blocks.WATER.getDefaultState(), false));

		add("world_lava_area", "Область лавы", "В нескольких блоках под ногами появляется лава. Осторожно!",
				Category.WORLD, 6, ctx -> {
					BlockPos base = feet(ctx).down(3);
					CardEffects.areaReplace(ctx.world(), base, 3, 1, Blocks.LAVA.getDefaultState(), false);
				});

		add("world_diamond_floor", "Алмазный пол", "Небольшая область под ногами становится алмазным блоком.",
				Category.WORLD, 6, ctx -> CardEffects.areaReplace(ctx.world(), feet(ctx), 2, 1, Blocks.DIAMOND_BLOCK.getDefaultState(), false));

		add("world_sand_world", "Песчаный мир", "С неба начинает падать песок.", Category.WORLD, 8,
				ctx -> CardEffects.spawnFallingBlocks(ctx.world(), feet(ctx), List.of(Blocks.SAND), 12, 4, 12, EFFECT_RANDOM));

		add("world_block_rain", "Дождь из блоков", "С неба падают случайные блоки.", Category.WORLD, 8,
				ctx -> CardEffects.spawnFallingBlocks(ctx.world(), feet(ctx),
						List.of(Blocks.COBBLESTONE, Blocks.DIRT, Blocks.GRAVEL, Blocks.SAND), 14, 5, 14, EFFECT_RANDOM));

		add("world_glass_trap", "Стеклянная ловушка", "Поверхность вокруг превращается в стекло.", Category.WORLD, 8,
				ctx -> CardEffects.areaReplace(ctx.world(), feet(ctx), 4, 1, Blocks.GLASS.getDefaultState(), false));

		add("world_swamp", "Болото", "Область вокруг превращается в грязь.", Category.WORLD, 7,
				ctx -> CardEffects.areaReplace(ctx.world(), feet(ctx), 5, 1, Blocks.MUD.getDefaultState(), false));

		add("world_gold_zone", "Золотая зона", "Область под ногами становится золотом на 20 секунд.",
				Category.WORLD, 7, ctx -> {
					var snapshot = CardEffects.areaReplaceSnapshot(ctx.world(), feet(ctx), 2, 1, Blocks.GOLD_BLOCK.getDefaultState());
					ctx.game().schedule(20 * 20, () -> CardEffects.restoreSnapshot(ctx.world(), snapshot));
				});

		add("world_broken_area", "Разрушенный чанк", "Часть блоков вокруг случайно исчезает.", Category.WORLD, 7,
				ctx -> {
					ServerWorld world = ctx.world();
					BlockPos center = feet(ctx);
					for (int dx = -4; dx <= 4; dx++) {
						for (int dz = -4; dz <= 4; dz++) {
							if (EFFECT_RANDOM.nextInt(3) != 0) {
								continue;
							}
							BlockPos pos = center.add(dx, -1, dz);
							if (!world.getBlockState(pos).isOf(Blocks.BEDROCK)) {
								world.setBlockState(pos, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_ALL);
							}
						}
					}
				});

		add("world_creeper_holes", "Криперская земля", "Рядом появляется несколько небольших ям.", Category.WORLD, 7,
				ctx -> {
					ServerWorld world = ctx.world();
					BlockPos center = feet(ctx);
					for (int i = 0; i < 3; i++) {
						BlockPos hole = center.add(EFFECT_RANDOM.nextInt(9) - 4, -1, EFFECT_RANDOM.nextInt(9) - 4);
						CardEffects.clearArea(world, hole, 1, 2);
					}
				});

		add("world_forest", "Лес за спиной", "Рядом мгновенно вырастает небольшая группа деревьев.", Category.WORLD, 7,
				ctx -> {
					ServerWorld world = ctx.world();
					BlockPos center = feet(ctx);
					for (int i = 0; i < 3; i++) {
						BlockPos trunkBase = center.add(EFFECT_RANDOM.nextInt(7) - 3, 0, EFFECT_RANDOM.nextInt(7) - 3);
						int height = 4 + EFFECT_RANDOM.nextInt(2);
						CardEffects.placeColumn(world, trunkBase, height, Blocks.OAK_LOG.getDefaultState());
						for (BlockPos leafPos : BlockPos.iterate(trunkBase.add(-2, height - 2, -2), trunkBase.add(2, height + 1, 2))) {
							if (world.getBlockState(leafPos).isAir() && EFFECT_RANDOM.nextInt(3) != 0) {
								world.setBlockState(leafPos, Blocks.OAK_LEAVES.getDefaultState(), net.minecraft.block.Block.NOTIFY_ALL);
							}
						}
					}
				});

		add("world_giant_mushroom", "Гигантский гриб", "Рядом появляется огромный гриб.", Category.WORLD, 6,
				ctx -> {
					ServerWorld world = ctx.world();
					BlockPos base = feet(ctx).add(2, 0, 0);
					var cap = EFFECT_RANDOM.nextBoolean() ? Blocks.RED_MUSHROOM_BLOCK : Blocks.BROWN_MUSHROOM_BLOCK;
					CardEffects.placeColumn(world, base, 5, Blocks.MUSHROOM_STEM.getDefaultState());
					for (BlockPos pos : BlockPos.iterate(base.add(-2, 4, -2), base.add(2, 6, 2))) {
						world.setBlockState(pos, cap.getDefaultState(), net.minecraft.block.Block.NOTIFY_ALL);
					}
				});

		add("world_well", "Колодец", "Прямо под ногами появляется небольшой каменный колодец с водой.",
				Category.WORLD, 6, ctx -> {
					ServerWorld world = ctx.world();
					BlockPos base = feet(ctx).down(1);
					for (BlockPos pos : BlockPos.iterate(base.add(-1, 0, -1), base.add(1, 2, 1))) {
						boolean edge = Math.abs(pos.getX() - base.getX()) == 1 || Math.abs(pos.getZ() - base.getZ()) == 1;
						if (edge) {
							world.setBlockState(pos, Blocks.STONE_BRICKS.getDefaultState(), net.minecraft.block.Block.NOTIFY_ALL);
						}
					}
					world.setBlockState(base, Blocks.WATER.getDefaultState(), net.minecraft.block.Block.NOTIFY_ALL);
				});

		add("world_reverse_well", "Обратный колодец", "Под ногами возникает яма, а сверху льётся вода.",
				Category.WORLD, 6, ctx -> {
					ServerWorld world = ctx.world();
					BlockPos center = feet(ctx);
					CardEffects.clearArea(world, center, 1, 3);
					BlockPos source = center.up(10);
					world.setBlockState(source, Blocks.WATER.getDefaultState(), net.minecraft.block.Block.NOTIFY_ALL);
					ctx.game().schedule(5 * 20, () -> world.setBlockState(source, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_ALL));
				});
	}

	// ==================================================================
	// 46-60. Мобы
	// ==================================================================

	static {
		add("mob_skeletons", "Скелетный привет", "Рядом появляются 3 скелета.", Category.MOBS, 9,
				ctx -> CardEffects.spawnMobs(ctx.world(), feet(ctx), EntityType.SKELETON, 3, 6, true, ctx.target(), EFFECT_RANDOM));

		add("mob_creepers", "Крипер-близнец", "Рядом появляются 2 крипера.", Category.MOBS, 8,
				ctx -> CardEffects.spawnMobs(ctx.world(), feet(ctx), EntityType.CREEPER, 2, 6, false, ctx.target(), EFFECT_RANDOM));

		add("mob_zombies", "Зомби-доставка", "Рядом появляются зомби.", Category.MOBS, 9,
				ctx -> CardEffects.spawnMobs(ctx.world(), feet(ctx), EntityType.ZOMBIE, 3, 5, true, ctx.target(), EFFECT_RANDOM));

		add("mob_spiders", "Паучья вечеринка", "Рядом появляются 5 пауков.", Category.MOBS, 8,
				ctx -> CardEffects.spawnMobs(ctx.world(), feet(ctx), EntityType.SPIDER, 5, 6, true, ctx.target(), EFFECT_RANDOM));

		add("mob_mini_raid", "Мини-рейд", "Рядом появляется небольшая группа враждебных мобов.", Category.MOBS, 7,
				ctx -> {
					CardEffects.spawnMobs(ctx.world(), feet(ctx), EntityType.ZOMBIE, 1, 6, true, ctx.target(), EFFECT_RANDOM);
					CardEffects.spawnMobs(ctx.world(), feet(ctx), EntityType.SKELETON, 1, 6, true, ctx.target(), EFFECT_RANDOM);
					CardEffects.spawnMobs(ctx.world(), feet(ctx), EntityType.HUSK, 1, 6, true, ctx.target(), EFFECT_RANDOM);
				});

		add("mob_slimes", "Слизь!", "Рядом появляются слизни.", Category.MOBS, 8,
				ctx -> CardEffects.spawnMobs(ctx.world(), feet(ctx), EntityType.SLIME, 3, 5, true, ctx.target(), EFFECT_RANDOM));

		add("mob_phantoms", "Летающая проблема", "Над игроком появляются фантомы.", Category.MOBS, 7,
				ctx -> CardEffects.spawnMobsAirborne(ctx.world(), feet(ctx), EntityType.PHANTOM, 2, 4, 12, EFFECT_RANDOM));

		add("mob_iron_guard", "Железная охрана", "Появляется недружелюбно настроенный железный голем.",
				Category.MOBS, 6, ctx -> CardEffects.spawnMobs(ctx.world(), feet(ctx), EntityType.IRON_GOLEM, 1, 5, true, ctx.target(), EFFECT_RANDOM));

		add("mob_wolf_pack", "Волчья стая", "Рядом появляется стая волков.", Category.MOBS, 7,
				ctx -> CardEffects.spawnMobs(ctx.world(), feet(ctx), EntityType.WOLF, 4, 6, true, ctx.target(), EFFECT_RANDOM));

		add("mob_shulker", "Шалкер", "Рядом появляется шалкер.", Category.MOBS, 6,
				ctx -> CardEffects.spawnMobs(ctx.world(), feet(ctx), EntityType.SHULKER, 1, 4, false, ctx.target(), EFFECT_RANDOM));

		add("mob_enderman", "Эндермен", "Рядом появляются 3 эндермена.", Category.MOBS, 7,
				ctx -> CardEffects.spawnMobs(ctx.world(), feet(ctx), EntityType.ENDERMAN, 3, 6, false, ctx.target(), EFFECT_RANDOM));

		add("mob_ghast", "Воздушная атака", "Над игроком появляются гасты.", Category.MOBS, 5,
				ctx -> CardEffects.spawnMobsAirborne(ctx.world(), feet(ctx), EntityType.GHAST, 2, 6, 20, EFFECT_RANDOM));

		add("mob_pig_bomb", "Свинка-сапёр", "Рядом появляется свинья — через несколько секунд она взрывается.",
				Category.MOBS, 6, ctx -> {
					ServerWorld world = ctx.world();
					var pig = EntityType.PIG.create(world, net.minecraft.entity.SpawnReason.EVENT);
					if (pig == null) {
						return;
					}
					BlockPos pos = feet(ctx).add(3, 0, 0);
					pig.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
					world.spawnEntity(pig);
					ctx.game().schedule(4 * 20, () -> {
						if (pig.isAlive()) {
							CardEffects.explode(world, pig.getX(), pig.getY(), pig.getZ(), 2.5f, false);
							pig.discard();
						}
					});
				});

		add("mob_boss", "Мини-босс", "Появляется сильный враждебный моб.", Category.LEGENDARY, 2,
				ctx -> CardEffects.spawnMobs(ctx.world(), feet(ctx), EntityType.EVOKER, 1, 5, true, ctx.target(), EFFECT_RANDOM));

		add("mob_wither", "Визер", "Легендарная карточка: рядом появляется иссушитель!", Category.LEGENDARY, 1,
				ctx -> CardEffects.spawnMobs(ctx.world(), feet(ctx), EntityType.WITHER, 1, 6, true, ctx.target(), EFFECT_RANDOM));
	}

	// ==================================================================
	// 61-70. Телепортация
	// ==================================================================

	static {
		add("tp_nether", "Прыжок в Незер", "Мгновенно переносит в Нижний мир.", Category.TELEPORT, 7, ctx -> {
			ServerPlayerEntity player = ctx.target();
			ServerWorld nether = ctx.server().getWorld(World.NETHER);
			if (nether == null) {
				return;
			}
			CardEffects.teleportSafe(player, nether, player.getX() / 8.0, player.getZ() / 8.0);
			potion(player, StatusEffects.FIRE_RESISTANCE, 15 * 20, 0);
		});

		add("tp_end", "Экскурсия в Энд", "Переносит на обсидиановую платформу в Энде.", Category.TELEPORT, 6, ctx -> {
			ServerPlayerEntity player = ctx.target();
			ServerWorld end = ctx.server().getWorld(World.END);
			if (end == null) {
				return;
			}
			player.teleport(end, 100.5, 50, 0.5, java.util.Set.of(), player.getYaw(), player.getPitch(), true);
		});

		add("tp_random_spot", "Случайная точка", "Телепортирует в случайное безопасное место поблизости.",
				Category.TELEPORT, 8, ctx -> {
					ServerPlayerEntity player = ctx.target();
					double x = player.getX() + (EFFECT_RANDOM.nextDouble() * 2 - 1) * 30;
					double z = player.getZ() + (EFFECT_RANDOM.nextDouble() * 2 - 1) * 30;
					CardEffects.teleportSafe(player, ctx.world(), x, z);
				});

		add("tp_back_in_time", "Назад во времени", "Возвращает туда, где игрок находился минуту назад.",
				Category.TELEPORT, 6, ctx -> ctx.game().teleportToPast(ctx.target(), 60));

		add("tp_swap_places", "Поменяться местами", "Выбравший и цель меняются координатами.", Category.TELEPORT, 6,
				ctx -> {
					ServerPlayerEntity chooser = ctx.chooser();
					ServerPlayerEntity target = ctx.target();
					if (chooser == target) {
						return;
					}
					double cx = chooser.getX(), cy = chooser.getY(), cz = chooser.getZ();
					ServerWorld cw = chooser.getEntityWorld();
					chooser.teleport(target.getEntityWorld(), target.getX(), target.getY(), target.getZ(),
							java.util.Set.of(), chooser.getYaw(), chooser.getPitch(), true);
					target.teleport(cw, cx, cy, cz, java.util.Set.of(), target.getYaw(), target.getPitch(), true);
				});

		add("tp_high_tourism", "Высотный туризм", "Переносит высоко вверх на прочную стеклянную площадку.",
				Category.TELEPORT, 7, ctx -> {
					ServerPlayerEntity player = ctx.target();
					BlockPos platform = new BlockPos((int) player.getX(), 200, (int) player.getZ());
					ctx.world().setBlockState(platform.down(), Blocks.GLASS.getDefaultState(), net.minecraft.block.Block.NOTIFY_ALL);
					player.teleport(ctx.world(), platform.getX() + 0.5, platform.getY(), platform.getZ() + 0.5,
							java.util.Set.of(), player.getYaw(), player.getPitch(), true);
				});

		add("tp_underground", "Подземелье", "Переносит глубоко под землю в безопасную полость.", Category.TELEPORT, 6,
				ctx -> {
					ServerPlayerEntity player = ctx.target();
					BlockPos deep = new BlockPos((int) player.getX(), -40, (int) player.getZ());
					CardEffects.clearAirPocket(ctx.world(), deep, 1, 2);
					player.teleport(ctx.world(), deep.getX() + 0.5, deep.getY(), deep.getZ() + 0.5,
							java.util.Set.of(), player.getYaw(), player.getPitch(), true);
				});

		add("tp_far_away", "Далеко-далеко", "Телепортирует на 500 блоков в случайном направлении.",
				Category.TELEPORT, 5, ctx -> {
					ServerPlayerEntity player = ctx.target();
					double angle = EFFECT_RANDOM.nextDouble() * Math.PI * 2;
					double x = player.getX() + Math.cos(angle) * 500;
					double z = player.getZ() + Math.sin(angle) * 500;
					CardEffects.teleportSafe(player, ctx.world(), x, z);
				});

		add("tp_fate_coordinates", "Координаты судьбы", "Случайная точка мира + 10 секунд неуязвимости.",
				Category.TELEPORT, 5, ctx -> {
					ServerPlayerEntity player = ctx.target();
					double x = (EFFECT_RANDOM.nextDouble() * 2 - 1) * 2000;
					double z = (EFFECT_RANDOM.nextDouble() * 2 - 1) * 2000;
					CardEffects.teleportSafe(player, ctx.world(), x, z);
					boolean was = player.isInvulnerable();
					player.setInvulnerable(true);
					ctx.game().schedule(10 * 20, () -> player.setInvulnerable(was));
				});

		add("tp_water_water", "Вода-вода", "Пытается телепортировать к ближайшему водоёму.", Category.TELEPORT, 5,
				ctx -> {
					ServerPlayerEntity player = ctx.target();
					ServerWorld world = ctx.world();
					for (int attempt = 0; attempt < 24; attempt++) {
						double x = player.getX() + (EFFECT_RANDOM.nextDouble() * 2 - 1) * 100;
						double z = player.getZ() + (EFFECT_RANDOM.nextDouble() * 2 - 1) * 100;
						int y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int) x, (int) z);
						BlockPos surface = new BlockPos((int) x, y - 1, (int) z);
						if (world.getBlockState(surface).isOf(Blocks.WATER)) {
							player.teleport(world, x, y, z, java.util.Set.of(), player.getYaw(), player.getPitch(), true);
							return;
						}
					}
					CardEffects.teleportSafe(player, world, player.getX() + 20, player.getZ() + 20);
				});
	}

	// ==================================================================
	// Дополнительно: телепорт в случайные биомы и на пик высоты
	// (добавлено по отдельной просьбе).
	// ==================================================================

	static {
		add("biome_deep_dark", "Мрачные недра", "Переносит в глубокий, зловещий биом Вардена.", Category.TELEPORT, 6,
				ctx -> teleportToBiome(ctx, World.OVERWORLD, net.minecraft.world.biome.BiomeKeys.DEEP_DARK, "Мрачные недра"));

		add("biome_pale_garden", "Бледный сад", "Переносит в жуткий туманный Бледный сад.", Category.TELEPORT, 6,
				ctx -> teleportToBiome(ctx, World.OVERWORLD, net.minecraft.world.biome.BiomeKeys.PALE_GARDEN, "Бледный сад"));

		add("biome_mushroom", "Грибные поля", "Переносит на остров грибных полей.", Category.TELEPORT, 6,
				ctx -> teleportToBiome(ctx, World.OVERWORLD, net.minecraft.world.biome.BiomeKeys.MUSHROOM_FIELDS, "Грибные поля"));

		add("biome_ice_spikes", "Ледяные пики", "Переносит в биом с ледяными шпилями.", Category.TELEPORT, 6,
				ctx -> teleportToBiome(ctx, World.OVERWORLD, net.minecraft.world.biome.BiomeKeys.ICE_SPIKES, "Ледяные пики"));

		add("biome_badlands", "Бесплодные земли", "Переносит в оранжевый каньон бесплодных земель.", Category.TELEPORT, 6,
				ctx -> teleportToBiome(ctx, World.OVERWORLD, net.minecraft.world.biome.BiomeKeys.BADLANDS, "Бесплодные земли"));

		add("biome_cherry_grove", "Вишнёвая роща", "Переносит в розовую вишнёвую рощу.", Category.TELEPORT, 6,
				ctx -> teleportToBiome(ctx, World.OVERWORLD, net.minecraft.world.biome.BiomeKeys.CHERRY_GROVE, "Вишнёвая роща"));

		add("biome_mangrove", "Мангровое болото", "Переносит в мангровое болото.", Category.TELEPORT, 6,
				ctx -> teleportToBiome(ctx, World.OVERWORLD, net.minecraft.world.biome.BiomeKeys.MANGROVE_SWAMP, "Мангровое болото"));

		add("biome_lush_caves", "Пышные пещеры", "Переносит в пышные пещеры под землёй.", Category.TELEPORT, 6,
				ctx -> teleportToBiome(ctx, World.OVERWORLD, net.minecraft.world.biome.BiomeKeys.LUSH_CAVES, "Пышные пещеры"));

		add("biome_dripstone", "Пещеры с капельником", "Переносит в пещеры с каменными сосульками.", Category.TELEPORT, 6,
				ctx -> teleportToBiome(ctx, World.OVERWORLD, net.minecraft.world.biome.BiomeKeys.DRIPSTONE_CAVES, "Пещеры с капельником"));

		add("biome_jungle", "Джунгли", "Переносит в густые джунгли.", Category.TELEPORT, 6,
				ctx -> teleportToBiome(ctx, World.OVERWORLD, net.minecraft.world.biome.BiomeKeys.JUNGLE, "Джунгли"));

		add("biome_desert", "Пустыня", "Переносит в жаркую пустыню.", Category.TELEPORT, 6,
				ctx -> teleportToBiome(ctx, World.OVERWORLD, net.minecraft.world.biome.BiomeKeys.DESERT, "Пустыня"));

		add("biome_frozen_ocean", "Ледяной океан", "Переносит в замёрзший океан.", Category.TELEPORT, 5,
				ctx -> teleportToBiome(ctx, World.OVERWORLD, net.minecraft.world.biome.BiomeKeys.FROZEN_OCEAN, "Ледяной океан"));

		add("biome_crimson_forest", "Багровый лес", "Переносит в багровый лес Нижнего мира.", Category.TELEPORT, 5,
				ctx -> teleportToBiome(ctx, World.NETHER, net.minecraft.world.biome.BiomeKeys.CRIMSON_FOREST, "Багровый лес"));

		add("biome_warped_forest", "Искажённый лес", "Переносит в искажённый лес Нижнего мира.", Category.TELEPORT, 5,
				ctx -> teleportToBiome(ctx, World.NETHER, net.minecraft.world.biome.BiomeKeys.WARPED_FOREST, "Искажённый лес"));

		add("biome_soul_sand_valley", "Долина Песка Душ", "Переносит в жуткую долину Песка Душ.", Category.TELEPORT, 5,
				ctx -> teleportToBiome(ctx, World.NETHER, net.minecraft.world.biome.BiomeKeys.SOUL_SAND_VALLEY, "Долина Песка Душ"));

		add("biome_basalt_deltas", "Базальтовые дельты", "Переносит в чёрно-серые базальтовые дельты.", Category.TELEPORT, 5,
				ctx -> teleportToBiome(ctx, World.NETHER, net.minecraft.world.biome.BiomeKeys.BASALT_DELTAS, "Базальтовые дельты"));

		add("tp_height_peak", "Пик высоты", "Переносит на самую высокую точку в ближайших чанках.", Category.TELEPORT, 7,
				ctx -> {
					ServerPlayerEntity player = ctx.target();
					ServerWorld world = ctx.world();
					int radius = 48;
					int step = 8;
					int baseX = (int) player.getX();
					int baseZ = (int) player.getZ();
					int bestY = Integer.MIN_VALUE;
					int bestX = baseX;
					int bestZ = baseZ;
					for (int dx = -radius; dx <= radius; dx += step) {
						for (int dz = -radius; dz <= radius; dz += step) {
							int x = baseX + dx;
							int z = baseZ + dz;
							// getTopY сам подгрузит/сгенерирует чанк при необходимости —
							// поэтому непрогруженные соседние чанки не ломают карту.
							int y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
							if (y > bestY) {
								bestY = y;
								bestX = x;
								bestZ = z;
							}
						}
					}
					player.teleport(world, bestX + 0.5, bestY + 1, bestZ + 0.5, java.util.Set.of(),
							player.getYaw(), player.getPitch(), true);
				});
	}

	// ==================================================================
	// 71-80. Инвентарь
	// ==================================================================

	static {
		add("inv_gift", "Подарок", "Игрок получает редкий предмет.", Category.INVENTORY, 8, ctx -> {
			var pool = List.of(Items.DIAMOND, Items.NETHERITE_SCRAP, Items.ENCHANTED_GOLDEN_APPLE, Items.EXPERIENCE_BOTTLE);
			CardEffects.giveItem(ctx.target(), new ItemStack(pool.get(EFFECT_RANDOM.nextInt(pool.size())), 1));
		});

		add("inv_junk", "Мусорщик", "В инвентаре появляются случайные предметы.", Category.INVENTORY, 9, ctx -> {
			var pool = List.of(Items.STICK, Items.COBBLESTONE, Items.FEATHER, Items.STRING, Items.FLINT, Items.BONE);
			for (int i = 0; i < 4; i++) {
				var item = pool.get(EFFECT_RANDOM.nextInt(pool.size()));
				CardEffects.giveItem(ctx.target(), new ItemStack(item, 1 + EFFECT_RANDOM.nextInt(8)));
			}
		});

		add("inv_one_slot", "Один слот", "Один случайный слот блокируется на 30 секунд (предмет временно исчезает).",
				Category.INVENTORY, 6, ctx -> {
					var slots = CardEffects.nonEmptySlots(ctx.target());
					if (slots.isEmpty()) {
						return;
					}
					int slot = slots.get(EFFECT_RANDOM.nextInt(slots.size()));
					ItemStack removed = ctx.target().getInventory().getStack(slot).copy();
					ctx.target().getInventory().setStack(slot, ItemStack.EMPTY);
					ServerPlayerEntity target = ctx.target();
					ctx.game().schedule(30 * 20, () -> {
						if (target.getInventory().getStack(slot).isEmpty()) {
							target.getInventory().setStack(slot, removed);
						} else {
							CardEffects.giveItem(target, removed);
						}
					});
				});

		add("inv_chaos", "Инвентарный хаос", "Порядок предметов в инвентаре перемешивается.", Category.INVENTORY, 8,
				ctx -> {
					var inv = ctx.target().getInventory().getMainStacks();
					List<ItemStack> stacks = new ArrayList<>(inv);
					java.util.Collections.shuffle(stacks, EFFECT_RANDOM);
					for (int i = 0; i < inv.size(); i++) {
						inv.set(i, stacks.get(i));
					}
				});

		add("inv_gold_gift", "Золотой подарок", "Игрок получает случайное количество золота.", Category.INVENTORY, 8,
				ctx -> CardEffects.giveItem(ctx.target(), new ItemStack(Items.GOLD_INGOT, 5 + EFFECT_RANDOM.nextInt(16))));

		add("inv_stone_kit", "Каменный набор", "В инвентарь добавляется большой набор камня.", Category.INVENTORY, 8,
				ctx -> CardEffects.giveItem(ctx.target(), new ItemStack(Items.COBBLESTONE, 64)));

		add("inv_vanish", "Исчезновение", "Один случайный предмет временно исчезает на 20 секунд.",
				Category.INVENTORY, 6, ctx -> {
					var slots = CardEffects.nonEmptySlots(ctx.target());
					if (slots.isEmpty()) {
						return;
					}
					int slot = slots.get(EFFECT_RANDOM.nextInt(slots.size()));
					ItemStack removed = ctx.target().getInventory().getStack(slot).copy();
					ctx.target().getInventory().setStack(slot, ItemStack.EMPTY);
					ServerPlayerEntity target = ctx.target();
					ctx.game().schedule(20 * 20, () -> {
						if (target.getInventory().getStack(slot).isEmpty()) {
							target.getInventory().setStack(slot, removed);
						} else {
							CardEffects.giveItem(target, removed);
						}
					});
				});

		add("inv_duplicator", "Дубликатор", "Один случайный предмет дублируется.", Category.INVENTORY, 7, ctx -> {
			var slots = CardEffects.nonEmptySlots(ctx.target());
			if (slots.isEmpty()) {
				return;
			}
			int slot = slots.get(EFFECT_RANDOM.nextInt(slots.size()));
			ItemStack stack = ctx.target().getInventory().getStack(slot);
			CardEffects.giveItem(ctx.target(), stack.copy());
		});

		add("inv_uninvited_item", "Незваный предмет", "В инвентаре появляется бесполезный, но забавный предмет.",
				Category.INVENTORY, 8, ctx -> {
					ItemStack stack = new ItemStack(Items.STICK, 1);
					stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
							net.minecraft.text.Text.literal("Абсолютно бесполезная палка"));
					CardEffects.giveItem(ctx.target(), stack);
				});

		add("inv_fate_slot", "Слот судьбы", "Один случайный предмет прячется на 20 секунд.", Category.INVENTORY, 6,
				ctx -> {
					var slots = CardEffects.nonEmptySlots(ctx.target());
					if (slots.isEmpty()) {
						return;
					}
					int slot = slots.get(EFFECT_RANDOM.nextInt(slots.size()));
					ItemStack removed = ctx.target().getInventory().getStack(slot).copy();
					ctx.target().getInventory().setStack(slot, ItemStack.EMPTY);
					ServerPlayerEntity target = ctx.target();
					ctx.game().schedule(20 * 20, () -> {
						if (target.getInventory().getStack(slot).isEmpty()) {
							target.getInventory().setStack(slot, removed);
						} else {
							CardEffects.giveItem(target, removed);
						}
					});
				});

		// Три карточки, которые прислал пользователь дополнительно:
		add("inv_ender_pearls", "Эндер-жемчужный набор", "Игрок получает 12 эндер-жемчугов.", Category.INVENTORY, 8,
				ctx -> CardEffects.giveItem(ctx.target(), new ItemStack(Items.ENDER_PEARL, 12)));

		add("inv_random_bag", "Мешок случайностей", "Несколько случайных полезных предметов.", Category.INVENTORY, 8,
				ctx -> {
					var pool = List.of(Items.IRON_INGOT, Items.DIAMOND, Items.EMERALD, Items.ARROW, Items.BREAD,
							Items.TORCH, Items.OAK_PLANKS, Items.GOLDEN_CARROT);
					for (int i = 0; i < 5; i++) {
						var item = pool.get(EFFECT_RANDOM.nextInt(pool.size()));
						CardEffects.giveItem(ctx.target(), new ItemStack(item, 1 + EFFECT_RANDOM.nextInt(16)));
					}
				});

		add("inv_dirt_disaster", "Земляная беда", "Весь инвентарь заполняется землёй. Легендарное наказание!",
				Category.LEGENDARY, 2, ctx -> {
					var inv = ctx.target().getInventory().getMainStacks();
					for (int i = 0; i < inv.size(); i++) {
						inv.set(i, new ItemStack(Items.DIRT, 64));
					}
				});
	}

	// ==================================================================
	// 81-90. Странности
	// ==================================================================

	static {
		// "Всё наоборот" (перевёрнутое управление) нельзя реализовать надёжно
		// без взлома клиентского ввода — заменено на головокружение (тошнота).
		add("weird_dizziness", "Головокружение", "Экран плывёт перед глазами 10 секунд.", Category.WEIRD, 9,
				ctx -> potion(ctx.target(), StatusEffects.NAUSEA, 10 * 20, 0));

		add("weird_huge_world", "Мир огромный", "Игрок уменьшается — мир кажется огромным на 40 секунд.",
				Category.WEIRD, 7, ctx -> tempAttribute(ctx, EntityAttributes.SCALE, -0.4,
						EntityAttributeModifier.Operation.ADD_VALUE, 40 * 20));

		add("weird_tiny_person", "Маленький человек", "Игрок становится крошечным на 60 секунд.", Category.WEIRD, 7,
				ctx -> tempAttribute(ctx, EntityAttributes.SCALE, -0.6,
						EntityAttributeModifier.Operation.ADD_VALUE, 60 * 20));

		add("weird_giant", "Гигант", "Игрок временно становится огромным на 45 секунд.", Category.WEIRD, 7,
				ctx -> tempAttribute(ctx, EntityAttributes.SCALE, 1.0,
						EntityAttributeModifier.Operation.ADD_VALUE, 45 * 20));

		add("weird_whisper", "Шёпот", "Рядом раздаётся один очень громкий, пугающий звук.", Category.WEIRD, 8,
				ctx -> ctx.target().playSoundToPlayer(net.minecraft.sound.SoundEvents.ENTITY_WITHER_SPAWN,
						net.minecraft.sound.SoundCategory.AMBIENT, 2f, 0.6f));

		add("weird_fast_day", "День за секунду", "Время суток резко меняется несколько раз подряд (для всех).",
				Category.WEIRD, 6, ctx -> ctx.game().scheduleRepeating(6, 5, i ->
						ctx.world().setTimeOfDay(ctx.world().getTimeOfDay() + 4000)));

		add("weird_item_rain", "Дождь предметов", "30 секунд с неба сыплются случайные мелкие предметы.",
				Category.WEIRD, 7, ctx -> {
					var pool = List.of(Items.APPLE, Items.STICK, Items.FEATHER, Items.CARROT, Items.EGG);
					ctx.game().scheduleRepeating(15, 40, i -> {
						ServerPlayerEntity target = ctx.game().server().getPlayerManager().getPlayer(ctx.target().getUuid());
						if (target == null) {
							return;
						}
						var item = pool.get(EFFECT_RANDOM.nextInt(pool.size()));
						var entity = new net.minecraft.entity.ItemEntity(target.getEntityWorld(),
								target.getX() + EFFECT_RANDOM.nextInt(5) - 2, target.getY() + 8, target.getZ() + EFFECT_RANDOM.nextInt(5) - 2,
								new ItemStack(item, 1));
						target.getEntityWorld().spawnEntity(entity);
					});
				});

		add("weird_invisible_wall", "Невидимый блок", "Перед игроком возникает невидимая преграда на 15 секунд.",
				Category.WEIRD, 7, ctx -> {
					ServerPlayerEntity player = ctx.target();
					Direction facing = player.getHorizontalFacing();
					BlockPos pos = player.getBlockPos().offset(facing, 2).up();
					ServerWorld world = ctx.world();
					var previous = world.getBlockState(pos);
					world.setBlockState(pos, Blocks.BARRIER.getDefaultState(), net.minecraft.block.Block.NOTIFY_ALL);
					ctx.game().schedule(15 * 20, () -> world.setBlockState(pos, previous, net.minecraft.block.Block.NOTIFY_ALL));
				});

		add("weird_trail", "След за собой", "20 секунд за игроком остаётся цветной след из блоков.",
				Category.WEIRD, 6, ctx -> {
					var palette = List.of(Blocks.RED_STAINED_GLASS, Blocks.LIME_STAINED_GLASS, Blocks.LIGHT_BLUE_STAINED_GLASS,
							Blocks.YELLOW_STAINED_GLASS, Blocks.PURPLE_STAINED_GLASS);
					ServerWorld world = ctx.world();
					UUID targetId = ctx.target().getUuid();
					ctx.game().scheduleRepeating(4, 100, i -> {
						ServerPlayerEntity target = ctx.game().server().getPlayerManager().getPlayer(targetId);
						if (target == null) {
							return;
						}
						BlockPos below = target.getBlockPos().down();
						if (world.getBlockState(below).isAir() || world.getBlockState(below).isReplaceable()) {
							return;
						}
						var original = world.getBlockState(below);
						var color = palette.get(EFFECT_RANDOM.nextInt(palette.size()));
						world.setBlockState(below, color.getDefaultState(), net.minecraft.block.Block.NOTIFY_ALL);
						ctx.game().schedule(4 * 20, () -> world.setBlockState(below, original, net.minecraft.block.Block.NOTIFY_ALL));
					});
				});

		// "Копия" (призрак-двойник, повторяющий движения игрока) требует
		// подставного игрока — вместо этого рядом появляется жутковатый
		// светящийся страж.
		add("weird_watcher", "Наблюдатель", "Рядом появляется жутковатая светящаяся фигура.", Category.WEIRD, 6,
				ctx -> CardEffects.spawnMobs(ctx.world(), feet(ctx), EntityType.ENDERMAN, 1, 5, false, ctx.target(), EFFECT_RANDOM));
	}

	// ==================================================================
	// 91-100. Легендарный хаос
	// ==================================================================

	static {
		add("legend_biome_surprise", "Чанк-сюрприз", "Область вокруг получает случайную тему.", Category.LEGENDARY, 3,
				ctx -> {
					var themes = List.of(Blocks.ICE, Blocks.SAND, Blocks.SNOW_BLOCK, Blocks.STONE, Blocks.MOSS_BLOCK);
					var theme = themes.get(EFFECT_RANDOM.nextInt(themes.size()));
					CardEffects.areaReplace(ctx.world(), feet(ctx), 6, 1, theme.getDefaultState(), false);
				});

		add("legend_sky_well", "Небесный колодец", "Над игроком появляется огромный столб воды.", Category.LEGENDARY, 3,
				ctx -> {
					BlockPos base = feet(ctx).up(15);
					CardEffects.placeColumn(ctx.world(), base, 15, Blocks.WATER.getDefaultState());
					ctx.game().schedule(6 * 20, () -> {
						for (int i = 0; i < 15; i++) {
							BlockPos pos = base.down(i);
							if (ctx.world().getBlockState(pos).isOf(Blocks.WATER)) {
								ctx.world().setBlockState(pos, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_ALL);
							}
						}
					});
				});

		add("legend_ground_gone", "Земля ушла", "Круглая область под ногами исчезает.", Category.LEGENDARY, 3,
				ctx -> CardEffects.clearCircle(ctx.world(), feet(ctx), 6, 3));

		add("legend_meteor", "Метеорит", "Через несколько секунд рядом падает огненный метеорит!", Category.LEGENDARY, 2,
				ctx -> {
					ServerWorld world = ctx.world();
					double x = ctx.target().getX() + (EFFECT_RANDOM.nextDouble() * 2 - 1) * 6;
					double z = ctx.target().getZ() + (EFFECT_RANDOM.nextDouble() * 2 - 1) * 6;
					ctx.target().sendMessage(net.minecraft.text.Text.literal("☄ Метеорит приближается!")
							.formatted(net.minecraft.util.Formatting.RED, net.minecraft.util.Formatting.BOLD), true);
					ctx.game().schedule(3 * 20, () -> {
						int y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int) x, (int) z);
						CardEffects.explode(world, x, y, z, 3.5f, true);
					});
				});

		add("legend_mob_surge", "Мир мобов", "Рядом мгновенно появляется большая волна враждебных мобов.",
				Category.LEGENDARY, 2, ctx -> {
					CardEffects.spawnMobs(ctx.world(), feet(ctx), EntityType.ZOMBIE, 3, 8, true, ctx.target(), EFFECT_RANDOM);
					CardEffects.spawnMobs(ctx.world(), feet(ctx), EntityType.SKELETON, 2, 8, true, ctx.target(), EFFECT_RANDOM);
					CardEffects.spawnMobs(ctx.world(), feet(ctx), EntityType.SPIDER, 2, 8, true, ctx.target(), EFFECT_RANDOM);
				});

		add("legend_dimension_swap", "Обмен измерениями", "Выбравший остаётся в обычном мире, а цель — в Незере.",
				Category.LEGENDARY, 2, ctx -> {
					ServerWorld nether = ctx.server().getWorld(World.NETHER);
					if (nether == null) {
						return;
					}
					ServerPlayerEntity target = ctx.target();
					CardEffects.teleportSafe(target, nether, target.getX() / 8.0, target.getZ() / 8.0);
					potion(target, StatusEffects.FIRE_RESISTANCE, 20 * 20, 0);
				});

		add("legend_fate_decides", "Судьба решила", "Судьба сама выбирает один из редких исходов.", Category.LEGENDARY, 3,
				ctx -> {
					Card chosen = randomCardExcluding(EFFECT_RANDOM, null);
					chosen.apply(ctx);
				});

		// "Поменять мир местами" (обмен целых областей карты) требует сложного
		// и рискованного копирования чанков — эта карта не реализована.

		add("legend_second_player", "Нечто в чужом обличье", "Рядом появляется сильный враждебный противник.",
				Category.LEGENDARY, 2, ctx -> CardEffects.spawnMobs(ctx.world(), feet(ctx), EntityType.VINDICATOR, 1, 4, true, ctx.target(), EFFECT_RANDOM));

		add("legend_chaos_button", "Кнопка ХАОСА", "Запускает 3 случайных карты подряд!", Category.LEGENDARY, 1,
				ctx -> {
					for (int i = 0; i < 3; i++) {
						Card random = randomCard(EFFECT_RANDOM);
						random.apply(ctx);
					}
				});
	}
}
