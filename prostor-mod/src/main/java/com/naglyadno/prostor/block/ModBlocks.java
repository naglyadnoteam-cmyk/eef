package com.naglyadno.prostor.block;

import com.naglyadno.prostor.ProstorMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.registry.CompostingChanceRegistry;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

import java.util.function.Function;

public final class ModBlocks {

	private ModBlocks() {
	}

	public static final Block REEDS = registerWithItem("reeds", ReedsBlock::new,
			AbstractBlock.Settings.create().mapColor(MapColor.DARK_GREEN).replaceable().noCollision().breakInstantly()
					.sounds(BlockSoundGroup.WET_GRASS).offset(AbstractBlock.OffsetType.XZ).burnable()
					.pistonBehavior(PistonBehavior.DESTROY));

	private static Block registerWithItem(String path, Function<AbstractBlock.Settings, Block> factory,
			AbstractBlock.Settings settings) {
		RegistryKey<Block> key = RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(ProstorMod.MOD_ID, path));
		Block block = factory.apply(settings.registryKey(key));
		Registry.register(Registries.BLOCK, key, block);
		RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(ProstorMod.MOD_ID, path));
		Registry.register(Registries.ITEM, itemKey,
				new BlockItem(block, new Item.Settings().registryKey(itemKey).useBlockPrefixedTranslationKey()));
		return block;
	}

	public static void init() {
		CompostingChanceRegistry.INSTANCE.add(REEDS, 0.5f);
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.NATURAL).register(entries -> entries.addAfter(Items.SUGAR_CANE, REEDS));
	}
}
