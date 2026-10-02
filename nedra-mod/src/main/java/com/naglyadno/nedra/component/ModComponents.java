package com.naglyadno.nedra.component;

import com.mojang.serialization.Codec;
import com.naglyadno.nedra.NedraMod;
import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModComponents {

	private ModComponents() {
	}

	/**
	 * Метка "этот компас сбит магнетитом": по ней мод отличает свою подмену трекера лодстоуна
	 * от настоящей привязки игрока к лодстоуну и умеет её корректно снять.
	 */
	public static final ComponentType<Boolean> MAGNETIZED = Registry.register(
			Registries.DATA_COMPONENT_TYPE,
			Identifier.of(NedraMod.MOD_ID, "magnetized"),
			ComponentType.<Boolean>builder().codec(Codec.BOOL).packetCodec(PacketCodecs.BOOLEAN).build());

	public static void init() {
		// регистрация выполняется в статических полях
	}
}
