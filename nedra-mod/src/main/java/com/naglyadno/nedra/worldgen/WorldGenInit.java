package com.naglyadno.nedra.worldgen;

import com.naglyadno.nedra.NedraMod;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.carver.ConfiguredCarver;
import net.minecraft.world.gen.feature.PlacedFeature;

/** Подключает руды, подземные фичи и вырезатели глубин мода ко всем биомам Overworld через Fabric API. */
public final class WorldGenInit {

	private WorldGenInit() {
	}

	public static void init() {
		// форма недр (пещеры биомов, реки, озёра, поселения) - самым первым, чтобы руды и украшения легли поверх
		add("deep_terrain_placed", GenerationStep.Feature.RAW_GENERATION);

		add("lumenite_ore_placed", GenerationStep.Feature.UNDERGROUND_ORES);
		add("magnetite_ore_placed", GenerationStep.Feature.UNDERGROUND_ORES);
		add("echo_ore_placed", GenerationStep.Feature.UNDERGROUND_ORES);
		add("unstable_stone_patch_placed", GenerationStep.Feature.UNDERGROUND_ORES);
		// ванильные руды и породы ярусов в глубинном сланце
		for (String ore : new String[]{"deep_ore_iron", "deep_ore_gold", "deep_ore_redstone", "deep_ore_lapis", "deep_ore_copper",
				"deep_ore_diamond", "deep_ore_diamond_large", "deep_ore_emerald", "deep_ore_tuff",
				"deep_smooth_basalt_blob", "deep_magma_blob", "deep_calcite_blob", "deep_tuff_blob"}) {
			add(ore, GenerationStep.Feature.UNDERGROUND_ORES);
		}
		add("deep_geode", GenerationStep.Feature.LOCAL_MODIFICATIONS);

		add("giant_cavern_placed", GenerationStep.Feature.LOCAL_MODIFICATIONS);
		add("magnetic_cavern_placed", GenerationStep.Feature.LOCAL_MODIFICATIONS);
		add("rare_underground_ocean_placed", GenerationStep.Feature.LOCAL_MODIFICATIONS);
		add("underground_village_placed", GenerationStep.Feature.UNDERGROUND_STRUCTURES);

		// разломы и мох ищут настоящий пол пещеры (environment_scan), поэтому идут после всех вырезаний
		add("current_vent_placed", GenerationStep.Feature.UNDERGROUND_DECORATION);
		add("deepmoss_patch_placed", GenerationStep.Feature.VEGETAL_DECORATION);
		add("deep_large_dripstone", GenerationStep.Feature.UNDERGROUND_DECORATION);
		add("deep_dripstone_cluster", GenerationStep.Feature.UNDERGROUND_DECORATION);
		add("deep_sculk", GenerationStep.Feature.UNDERGROUND_DECORATION);
		add("deep_glow_lichen", GenerationStep.Feature.VEGETAL_DECORATION);

		// самым последним: осушение глубинных пещер и биомы ярусов - после всех руд и украшений
		add("deep_layers_placed", GenerationStep.Feature.TOP_LAYER_MODIFICATION);

		// пещеры и ущелья новых глубин (ванильные вырезатели оставлены на своих ванильных высотах)
		addCarver("deep_cave");
		addCarver("deep_canyon");
	}

	private static void addCarver(String path) {
		RegistryKey<ConfiguredCarver<?>> key = RegistryKey.of(RegistryKeys.CONFIGURED_CARVER, Identifier.of(NedraMod.MOD_ID, path));
		BiomeModifications.addCarver(BiomeSelectors.foundInOverworld(), key);
	}

	private static void add(String path, GenerationStep.Feature step) {
		RegistryKey<PlacedFeature> key = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(NedraMod.MOD_ID, path));
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), step, key);
	}
}
