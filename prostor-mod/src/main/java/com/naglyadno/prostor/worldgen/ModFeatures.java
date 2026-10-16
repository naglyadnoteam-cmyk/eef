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
			register("lake_district_lakes", new LakeFeature(DefaultFeatureConfig.CODEC, Lakes.LAKE_DISTRICT));

	public static final Feature<DefaultFeatureConfig> LAKE_DISTRICT_SHORE =
			register("lake_district_shore", new LakeShoreFeature(DefaultFeatureConfig.CODEC, Lakes.LAKE_DISTRICT));

	public static final Feature<DefaultFeatureConfig> MARSH_POOLS =
			register("marsh_pools", new LakeFeature(DefaultFeatureConfig.CODEC, Lakes.MARSH));

	public static final Feature<DefaultFeatureConfig> MARSH_SHORE =
			register("marsh_shore", new LakeShoreFeature(DefaultFeatureConfig.CODEC, Lakes.MARSH));

	public static final Feature<DefaultFeatureConfig> MARSH_GROUND =
			register("marsh_ground", new MarshGroundFeature(DefaultFeatureConfig.CODEC));

	public static final Feature<DefaultFeatureConfig> FLOODPLAIN =
			register("floodplain", new FloodplainFeature(DefaultFeatureConfig.CODEC));

	public static final Feature<DefaultFeatureConfig> CANYON =
			register("river_canyon", new CanyonFeature(DefaultFeatureConfig.CODEC));

	public static final Feature<DefaultFeatureConfig> WATERFALLS =
			register("waterfalls", new WaterfallFeature(DefaultFeatureConfig.CODEC));

	private ModFeatures() {
	}

	private static <C extends FeatureConfig, F extends Feature<C>> F register(String path, F feature) {
		return Registry.register(Registries.FEATURE, Identifier.of(ProstorMod.MOD_ID, path), feature);
	}

	public static void init() {
		// регистрация выполняется в статических полях
	}
}
