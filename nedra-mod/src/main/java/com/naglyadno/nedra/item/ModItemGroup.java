package com.naglyadno.nedra.item;

import com.naglyadno.nedra.NedraMod;
import com.naglyadno.nedra.block.ModBlocks;
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

	public static ItemGroup NEDRA;

	public static void init() {
		RegistryKey<ItemGroup> key = RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.of(NedraMod.MOD_ID, "nedra"));
		NEDRA = Registry.register(Registries.ITEM_GROUP, key, FabricItemGroup.builder()
				.icon(() -> new ItemStack(ModBlocks.LUMENITE_ORE))
				.displayName(Text.translatable("itemGroup.nedra"))
				.entries((context, entries) -> {
					entries.add(ModBlocks.LUMENITE_ORE);
					entries.add(ModBlocks.MAGNETITE_ORE);
					entries.add(ModBlocks.ECHO_ORE);
					entries.add(ModBlocks.UNSTABLE_STONE);
					entries.add(ModBlocks.CURRENT_VENT);
					entries.add(ModBlocks.DEEPMOSS);
					entries.add(ModItems.DEEPMOSS_CLUMP);
					entries.add(ModItems.PRESSURE_TABLET);
					entries.add(ModItems.GEOPHONE);
					entries.add(ModItems.HELMET_LIGHT);
					entries.add(ModItems.HELMET_REINFORCED);
					entries.add(ModItems.HELMET_DEEPSUIT);
				})
				.build());
	}
}
