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

		addFeature("giant_cavern_placed", GenerationStep.Feature.LOCAL_MODIFICATIONS);
		addFeature("rare_underground_ocean_placed", GenerationStep.Feature.LOCAL_MODIFICATIONS);
		addFeature("underground_river_placed", GenerationStep.Feature.LAKES);
		addFeature("underground_village_placed", GenerationStep.Feature.UNDERGROUND_STRUCTURES);
	}

	private static void addOre(String path) {
		addFeature(path, GenerationStep.Feature.UNDERGROUND_ORES);
	}

	private static void addFeature(String path, GenerationStep.Feature step) {
		RegistryKey<PlacedFeature> key = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(NedraMod.MOD_ID, path));
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), step, key);
	}
}
