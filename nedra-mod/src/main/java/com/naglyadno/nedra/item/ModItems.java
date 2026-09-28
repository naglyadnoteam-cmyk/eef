package com.naglyadno.nedra.item;

import com.naglyadno.nedra.NedraMod;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.equipment.ArmorMaterial;
import net.minecraft.item.equipment.ArmorMaterials;
import net.minecraft.item.equipment.EquipmentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

import java.util.Optional;
import java.util.function.Function;

public final class ModItems {

	private ModItems() {
	}

	public static final Item DEEPMOSS_CLUMP = register("deepmoss_clump", Item::new, new Item.Settings());

	public static final Item PRESSURE_TABLET = register("pressure_tablet", PressureTabletItem::new,
			new Item.Settings().maxCount(16));

	public static final Item GEOPHONE = register("geophone", GeophoneItem::new,
			new Item.Settings().maxCount(1).maxDamage(64));

	public static final Item HELMET_LIGHT = registerHelmet("helmet_light", ArmorMaterials.IRON);
	public static final Item HELMET_REINFORCED = registerHelmet("helmet_reinforced", ArmorMaterials.DIAMOND);
	public static final Item HELMET_DEEPSUIT = registerHelmet("helmet_deepsuit", ArmorMaterials.NETHERITE);

	private static Item register(String path, Function<Item.Settings, Item> factory, Item.Settings settings) {
		RegistryKey<Item> key = itemKey(path);
		Item item = factory.apply(settings.registryKey(key));
		Registry.register(Registries.ITEM, key, item);
		return item;
	}

	/**
	 * С 1.21.2 отдельного класса ArmorItem больше нет - броня это обычный Item с компонентами
	 * EQUIPPABLE (какой слот, звук/модель экипировки) и ATTRIBUTE_MODIFIERS (защита/прочность,
	 * которые берутся из существующего ArmorMaterial, чтобы не изобретать свой материал с нуля).
	 */
	private static Item registerHelmet(String path, RegistryEntry<ArmorMaterial> materialEntry) {
		ArmorMaterial material = materialEntry.value();
		RegistryKey<Item> key = itemKey(path);

		EquippableComponent equippable = new EquippableComponent(
				EquipmentSlot.HEAD,
				material.equipSound(),
				Optional.of(material.assetId()),
				Optional.empty(),
				Optional.empty(),
				true,
				true,
				true,
				false,
				false,
				RegistryEntry.of(SoundEvents.ITEM_SHEARS_SNIP)
		);

		Item.Settings settings = new Item.Settings()
				.registryKey(key)
				.maxCount(1)
				.maxDamage(material.durability())
				.component(DataComponentTypes.EQUIPPABLE, equippable)
				.component(DataComponentTypes.ATTRIBUTE_MODIFIERS, material.createAttributeModifiers(EquipmentType.HELMET));

		Item item = new Item(settings);
		Registry.register(Registries.ITEM, key, item);
		return item;
	}

	private static RegistryKey<Item> itemKey(String path) {
		return RegistryKey.of(RegistryKeys.ITEM, Identifier.of(NedraMod.MOD_ID, path));
	}

	public static void init() {
		// сама регистрация происходит в статических полях выше; метод нужен, чтобы класс точно
		// загрузился (и поля инициализировались) в момент вызова из NedraMod.onInitialize().
	}
}
