package com.naglyadno.nedra.worldgen.feature;

import com.naglyadno.nedra.NedraMod;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.FeatureConfig;

public final class ModFeatures {

	private ModFeatures() {
	}

	public static final Feature<GiantCavernFeature.Config> GIANT_CAVERN =
			register("giant_cavern", new GiantCavernFeature(GiantCavernFeature.Config.CODEC));

	public static final Feature<UndergroundRiverFeature.Config> UNDERGROUND_RIVER =
			register("underground_river", new UndergroundRiverFeature(UndergroundRiverFeature.Config.CODEC));

	public static final Feature<UndergroundVillageFeature.Config> UNDERGROUND_VILLAGE =
			register("underground_village", new UndergroundVillageFeature(UndergroundVillageFeature.Config.CODEC));

	public static final Feature<DefaultFeatureConfig> DEEP_LAYERS =
			register("deep_layers", new DeepLayersFeature(DefaultFeatureConfig.CODEC));

	private static <C extends FeatureConfig, F extends Feature<C>> F register(String path, F feature) {
		return Registry.register(Registries.FEATURE, Identifier.of(NedraMod.MOD_ID, path), feature);
	}

	public static void init() {
		// Обращение к классу достаточно, чтобы выполнилась статическая регистрация выше.
	}
}
