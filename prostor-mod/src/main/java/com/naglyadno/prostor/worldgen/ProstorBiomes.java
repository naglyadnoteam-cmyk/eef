package com.naglyadno.prostor.worldgen;

import com.naglyadno.prostor.ProstorMod;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;

import java.util.List;

public final class ProstorBiomes {

	public static final RegistryKey<Biome> LAKE_DISTRICT = key("lake_district");
	public static final RegistryKey<Biome> FLOODPLAIN = key("floodplain");
	public static final RegistryKey<Biome> RIVER_CANYON = key("river_canyon");
	public static final RegistryKey<Biome> WATERFALL_CLIFFS = key("waterfall_cliffs");
	public static final RegistryKey<Biome> MARSH = key("marsh");

	/** Все биомы мода: для команд /prostor и проверок. */
	public static final List<RegistryKey<Biome>> ALL = List.of(LAKE_DISTRICT, FLOODPLAIN, RIVER_CANYON, WATERFALL_CLIFFS, MARSH);

	private ProstorBiomes() {
	}

	private static RegistryKey<Biome> key(String path) {
		return RegistryKey.of(RegistryKeys.BIOME, Identifier.of(ProstorMod.MOD_ID, path));
	}
}
