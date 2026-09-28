package com.naglyadno.nedra.effect;

import com.naglyadno.nedra.NedraMod;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryEntry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public final class ModEffects {

	private ModEffects() {
	}

	public static RegistryEntry<StatusEffect> PRESSURE_RESISTANCE;

	public static void init() {
		RegistryKey<StatusEffect> key = RegistryKey.of(RegistryKeys.STATUS_EFFECT,
				Identifier.of(NedraMod.MOD_ID, "pressure_resistance"));
		StatusEffect effect = new StatusEffect(StatusEffectCategory.BENEFICIAL, 0x3388ff) {
		};
		PRESSURE_RESISTANCE = Registry.registerReference(Registries.STATUS_EFFECT, key, effect);
	}
}
