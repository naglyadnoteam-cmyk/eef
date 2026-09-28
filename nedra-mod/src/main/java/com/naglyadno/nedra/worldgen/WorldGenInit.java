package com.naglyadno.nedra.worldgen;

import com.naglyadno.nedra.NedraMod;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.PlacedFeature;

/** Подключает руды мода к обычной генерации существующих биомов Overworld через Fabric API. */
public final class WorldGenInit {

	private WorldGenInit() {
	}

	public static void init() {
		addOre("lumenite_ore_placed");
		addOre("magnetite_ore_placed");
		addOre("echo_ore_placed");
		addOre("unstable_stone_patch_placed");
		addOre("current_vent_placed");
	}

	private static void addOre(String path) {
		RegistryKey<PlacedFeature> key = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(NedraMod.MOD_ID, path));
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Feature.UNDERGROUND_ORES, key);
	}
}
