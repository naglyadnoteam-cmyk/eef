package com.naglyadno.nedra.item;

import com.naglyadno.nedra.NedraMod;
import com.naglyadno.nedra.block.ModBlocks;
import com.naglyadno.nedra.guide.GuideBook;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class ModItemGroup {

	private ModItemGroup() {
	}

	public static void init() {
		RegistryKey<ItemGroup> key = RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.of(NedraMod.MOD_ID, "nedra"));
		Registry.register(Registries.ITEM_GROUP, key, FabricItemGroup.builder()
				.icon(() -> new ItemStack(ModItems.HELMET_DEEPSUIT))
				.displayName(Text.translatable("itemGroup.nedra"))
				.entries((context, entries) -> {
					entries.add(GuideBook.create());
					entries.add(ModItems.HELMET_LIGHT);
					entries.add(ModItems.HELMET_REINFORCED);
					entries.add(ModItems.HELMET_DEEPSUIT);
					entries.add(ModItems.PRESSURE_TABLET);
					entries.add(ModItems.GEOPHONE);
					entries.add(ModItems.LUMENITE_CRYSTAL);
					entries.add(ModItems.RAW_MAGNETITE);
					entries.add(ModItems.MAGNETITE_INGOT);
					entries.add(ModItems.RESONANT_SHARD);
					entries.add(ModItems.DEEPMOSS_CLUMP);
					entries.add(ModBlocks.LUMENITE_LAMP);
					entries.add(ModBlocks.LUMENITE_ORE);
					entries.add(ModBlocks.MAGNETITE_ORE);
					entries.add(ModBlocks.ECHO_ORE);
					entries.add(ModBlocks.UNSTABLE_STONE);
					entries.add(ModBlocks.CURRENT_VENT);
					entries.add(ModBlocks.DEEPMOSS);
				})
				.build());
	}
}
