package com.naglyadno.nedra.pressure;

import com.naglyadno.nedra.NedraMod;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

/** Тип урона "давление" (data/nedra/damage_type/pressure.json): игнорирует броню и не отбрасывает. */
public final class ModDamageTypes {

	private ModDamageTypes() {
	}

	public static final RegistryKey<DamageType> PRESSURE =
			RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(NedraMod.MOD_ID, "pressure"));

	public static DamageSource pressure(ServerWorld world) {
		return new DamageSource(world.getRegistryManager().getOrThrow(RegistryKeys.DAMAGE_TYPE).getOrThrow(PRESSURE));
	}
}
