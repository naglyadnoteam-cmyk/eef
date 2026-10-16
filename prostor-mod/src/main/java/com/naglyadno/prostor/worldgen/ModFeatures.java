package com.naglyadno.prostor.worldgen;

import com.naglyadno.prostor.ProstorMod;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.FeatureConfig;

public final class ModFeatures {

	public static final Feature<DefaultFeatureConfig> LAKE_DISTRICT_LAKES =
			register("lake_district_lakes", new LakeDistrictLakesFeature(DefaultFeatureConfig.CODEC));

	public static final Feature<DefaultFeatureConfig> LAKE_DISTRICT_SHORE =
			register("lake_district_shore", new LakeDistrictShoreFeature(DefaultFeatureConfig.CODEC));

	private ModFeatures() {
	}

	private static <C extends FeatureConfig, F extends Feature<C>> F register(String path, F feature) {
		return Registry.register(Registries.FEATURE, Identifier.of(ProstorMod.MOD_ID, path), feature);
	}

	public static void init() {
		// регистрация выполняется в статических полях
	}
}
