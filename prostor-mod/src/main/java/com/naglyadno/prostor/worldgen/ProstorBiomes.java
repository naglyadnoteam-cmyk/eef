package com.naglyadno.prostor.worldgen;

import com.naglyadno.prostor.ProstorMod;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;

import java.util.List;

public final class ProstorBiomes {

	public static final RegistryKey<Biome> LAKE_DISTRICT = key("lake_district");

	/** Все биомы мода: для команд /prostor и проверок. */
	public static final List<RegistryKey<Biome>> ALL = List.of(LAKE_DISTRICT);

	private ProstorBiomes() {
	}

	private static RegistryKey<Biome> key(String path) {
		return RegistryKey.of(RegistryKeys.BIOME, Identifier.of(ProstorMod.MOD_ID, path));
	}
}
