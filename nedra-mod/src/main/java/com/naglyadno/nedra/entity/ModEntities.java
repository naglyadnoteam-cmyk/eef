package com.naglyadno.nedra.entity;

import com.naglyadno.nedra.NedraMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.Heightmap;

public final class ModEntities {

	private ModEntities() {
	}

	private static final RegistryKey<EntityType<?>> RUST_BRUTE_KEY =
			RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(NedraMod.MOD_ID, "rust_brute"));

	/** Размеры как у зомби: рост громилы задаёт атрибут масштаба, он же увеличивает модель и хитбокс. */
	public static final EntityType<RustBruteEntity> RUST_BRUTE = Registry.register(Registries.ENTITY_TYPE, RUST_BRUTE_KEY,
			EntityType.Builder.create(RustBruteEntity::new, SpawnGroup.MONSTER)
					.dimensions(0.6F, 1.95F)
					.eyeHeight(1.74F)
					.passengerAttachments(2.075F)
					.vehicleAttachment(-0.7F)
					.maxTrackingRange(8)
					.notAllowedInPeaceful()
					.build(RUST_BRUTE_KEY));

	public static void init() {
		FabricDefaultAttributeRegistry.register(RUST_BRUTE, RustBruteEntity.createRustBruteAttributes());
		// как у обычного зомби: на твёрдом полу и только в темноте
		SpawnRestriction.register(RUST_BRUTE, SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
				HostileEntity::canSpawnInDark);
	}
}
