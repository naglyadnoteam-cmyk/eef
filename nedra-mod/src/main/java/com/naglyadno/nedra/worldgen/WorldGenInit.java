package com.naglyadno.nedra.worldgen;

import com.naglyadno.nedra.NedraMod;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.PlacedFeature;

/** Подключает руды и подземные фичи мода к генерации всех биомов Overworld через Fabric API. */
public final class WorldGenInit {

	private WorldGenInit() {
	}

	public static void init() {
		add("lumenite_ore_placed", GenerationStep.Feature.UNDERGROUND_ORES);
		add("magnetite_ore_placed", GenerationStep.Feature.UNDERGROUND_ORES);
		add("echo_ore_placed", GenerationStep.Feature.UNDERGROUND_ORES);
		add("unstable_stone_patch_placed", GenerationStep.Feature.UNDERGROUND_ORES);

		add("giant_cavern_placed", GenerationStep.Feature.LOCAL_MODIFICATIONS);
		add("magnetic_cavern_placed", GenerationStep.Feature.LOCAL_MODIFICATIONS);
		add("rare_underground_ocean_placed", GenerationStep.Feature.LOCAL_MODIFICATIONS);
		add("underground_lake_placed", GenerationStep.Feature.LAKES);
		add("underground_river_placed", GenerationStep.Feature.LAKES);
		add("underground_village_placed", GenerationStep.Feature.UNDERGROUND_STRUCTURES);

		// разломы и мох ищут настоящий пол пещеры (environment_scan), поэтому идут после всех вырезаний
		add("current_vent_placed", GenerationStep.Feature.UNDERGROUND_DECORATION);
		add("deepmoss_patch_placed", GenerationStep.Feature.VEGETAL_DECORATION);
	}

	private static void add(String path, GenerationStep.Feature step) {
		RegistryKey<PlacedFeature> key = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(NedraMod.MOD_ID, path));
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), step, key);
	}
}
