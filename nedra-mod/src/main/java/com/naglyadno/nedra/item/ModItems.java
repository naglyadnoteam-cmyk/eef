package com.naglyadno.nedra.item;

import com.naglyadno.nedra.NedraMod;
import com.naglyadno.nedra.entity.ModEntities;
import com.naglyadno.nedra.sound.ModSounds;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ConsumableComponent;
import net.minecraft.component.type.ConsumableComponents;
import net.minecraft.item.Item;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.item.equipment.ArmorMaterial;
import net.minecraft.item.equipment.EquipmentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

import java.util.function.Function;

public final class ModItems {

	private ModItems() {
	}

	// --- сырьё ---
	public static final Item LUMENITE_CRYSTAL = register("lumenite_crystal", Item::new, new Item.Settings());
	public static final Item RAW_MAGNETITE = register("raw_magnetite", Item::new, new Item.Settings());
	public static final Item MAGNETITE_INGOT = register("magnetite_ingot", Item::new, new Item.Settings());
	public static final Item RESONANT_SHARD = register("resonant_shard", Item::new,
			new Item.Settings().rarity(Rarity.UNCOMMON));
	public static final Item DEEPMOSS_CLUMP = register("deepmoss_clump", Item::new, new Item.Settings());
	public static final Item SCARLET_SHARD = register("scarlet_shard", Item::new, new Item.Settings());
	public static final Item FROST_SHARD = register("frost_shard", Item::new, new Item.Settings());

	// --- снаряжение ---
	private static final ConsumableComponent TABLET_CONSUMABLE = ConsumableComponents.food()
			.consumeSeconds(0.8f)
			.consumeParticles(false)
			.finishSound(ModSounds.TABLET_SWALLOW)
			.build();

	public static final Item PRESSURE_TABLET = register("pressure_tablet", PressureTabletItem::new,
			new Item.Settings().maxCount(16).component(DataComponentTypes.CONSUMABLE, TABLET_CONSUMABLE));

	public static final Item GEOPHONE = register("geophone", GeophoneItem::new,
			new Item.Settings().maxCount(1).maxDamage(128).enchantable(10));

	public static final Item HELMET_LIGHT = registerHelmet("helmet_light", ModArmorMaterials.MINER, Rarity.COMMON);
	public static final Item HELMET_REINFORCED = registerHelmet("helmet_reinforced", ModArmorMaterials.REINFORCED, Rarity.UNCOMMON);
	public static final Item HELMET_DEEPSUIT = registerHelmet("helmet_deepsuit", ModArmorMaterials.DEEPSUIT, Rarity.RARE);

	// --- яйца призыва ---
	public static final Item RUST_BRUTE_SPAWN_EGG = register("rust_brute_spawn_egg", SpawnEggItem::new,
			new Item.Settings().spawnEgg(ModEntities.RUST_BRUTE));
	public static final Item OVERGROWN_ZOMBIE_SPAWN_EGG = register("overgrown_zombie_spawn_egg", SpawnEggItem::new,
			new Item.Settings().spawnEgg(ModEntities.OVERGROWN_ZOMBIE));
	public static final Item OVERGROWN_SKELETON_SPAWN_EGG = register("overgrown_skeleton_spawn_egg", SpawnEggItem::new,
			new Item.Settings().spawnEgg(ModEntities.OVERGROWN_SKELETON));
	public static final Item OVERGROWN_CREEPER_SPAWN_EGG = register("overgrown_creeper_spawn_egg", SpawnEggItem::new,
			new Item.Settings().spawnEgg(ModEntities.OVERGROWN_CREEPER));
	public static final Item FROSTBITTEN_SPAWN_EGG = register("frostbitten_spawn_egg", SpawnEggItem::new,
			new Item.Settings().spawnEgg(ModEntities.FROSTBITTEN));
	public static final Item PRISM_GALE_SPAWN_EGG = register("prism_gale_spawn_egg", SpawnEggItem::new,
			new Item.Settings().spawnEgg(ModEntities.PRISM_GALE));

	private static Item register(String path, Function<Item.Settings, Item> factory, Item.Settings settings) {
		RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(NedraMod.MOD_ID, path));
		Item item = factory.apply(settings.registryKey(key));
		Registry.register(Registries.ITEM, key, item);
		return item;
	}

	/**
	 * Ванильный Item.Settings#armor сам выставляет правильную прочность (база материала x11 для
	 * шлема), атрибуты защиты, зачаровываемость, слот экипировки с моделью и ремонт в наковальне.
	 */
	private static Item registerHelmet(String path, ArmorMaterial material, Rarity rarity) {
		return register(path, Item::new, new Item.Settings().armor(material, EquipmentType.HELMET).rarity(rarity));
	}

	public static void init() {
		// регистрация выполняется в статических полях
	}
}
