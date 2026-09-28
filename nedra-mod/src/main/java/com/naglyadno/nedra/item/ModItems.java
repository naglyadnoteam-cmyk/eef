package com.naglyadno.nedra.item;

import com.naglyadno.nedra.NedraMod;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class ModItems {

	private ModItems() {
	}

	public static final Item DEEPMOSS_CLUMP = register("deepmoss_clump", Item::new, new Item.Settings());

	public static final Item PRESSURE_TABLET = register("pressure_tablet", PressureTabletItem::new,
			new Item.Settings().maxCount(16));

	public static final Item GEOPHONE = register("geophone", GeophoneItem::new,
			new Item.Settings().maxCount(1).maxDamage(64));

	private static Item register(String path, java.util.function.Function<Item.Settings, Item> factory, Item.Settings settings) {
		RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(NedraMod.MOD_ID, path));
		Item item = factory.apply(settings.registryKey(key));
		Registry.register(Registries.ITEM, key, item);
		return item;
	}

	public static void init() {
		// сама регистрация происходит в статических полях выше; метод нужен, чтобы класс точно
		// загрузился (и поля инициализировались) в момент вызова из NedraMod.onInitialize().
	}
}
