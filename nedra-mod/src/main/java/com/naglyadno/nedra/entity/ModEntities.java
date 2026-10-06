package com.naglyadno.nedra.entity;

import com.naglyadno.nedra.NedraMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.ZombieEntity;
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

	public static final EntityType<OvergrownZombieEntity> OVERGROWN_ZOMBIE = register("overgrown_zombie",
			EntityType.Builder.create(OvergrownZombieEntity::new, SpawnGroup.MONSTER)
					.dimensions(0.6F, 1.95F)
					.eyeHeight(1.74F)
					.passengerAttachments(2.075F)
					.vehicleAttachment(-0.7F)
					.maxTrackingRange(8)
					.notAllowedInPeaceful());

	public static final EntityType<OvergrownSkeletonEntity> OVERGROWN_SKELETON = register("overgrown_skeleton",
			EntityType.Builder.create(OvergrownSkeletonEntity::new, SpawnGroup.MONSTER)
					.dimensions(0.6F, 1.99F)
					.eyeHeight(1.74F)
					.vehicleAttachment(-0.7F)
					.maxTrackingRange(8)
					.notAllowedInPeaceful());

	public static final EntityType<OvergrownCreeperEntity> OVERGROWN_CREEPER = register("overgrown_creeper",
			EntityType.Builder.create(OvergrownCreeperEntity::new, SpawnGroup.MONSTER)
					.dimensions(0.6F, 1.7F)
					.maxTrackingRange(8)
					.notAllowedInPeaceful());

	private static <T extends Entity> EntityType<T> register(String path, EntityType.Builder<T> builder) {
		RegistryKey<EntityType<?>> key = RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(NedraMod.MOD_ID, path));
		return Registry.register(Registries.ENTITY_TYPE, key, builder.build(key));
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(OVERGROWN_ZOMBIE, ZombieEntity.createZombieAttributes());
		FabricDefaultAttributeRegistry.register(OVERGROWN_SKELETON, AbstractSkeletonEntity.createAbstractSkeletonAttributes());
		FabricDefaultAttributeRegistry.register(OVERGROWN_CREEPER, CreeperEntity.createCreeperAttributes());
		registerDarkSpawn(OVERGROWN_ZOMBIE);
		registerDarkSpawn(OVERGROWN_SKELETON);
		registerDarkSpawn(OVERGROWN_CREEPER);
		FabricDefaultAttributeRegistry.register(RUST_BRUTE, RustBruteEntity.createRustBruteAttributes());
		// как у обычного зомби: на твёрдом полу и только в темноте
		SpawnRestriction.register(RUST_BRUTE, SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
				HostileEntity::canSpawnInDark);
	}

	private static <T extends HostileEntity> void registerDarkSpawn(EntityType<T> type) {
		SpawnRestriction.register(type, SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
				HostileEntity::canSpawnInDark);
	}
}
