package com.naglyadno.nedra.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.BreezeEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.storage.ReadView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

/**
 * Хрустальный вихрь - страж Хрустальной цитадели. Ведёт себя как ванильный вихрь (прыжки, заряды ветра),
 * но синий и быстрее; здоровья у него вдвое меньше, а заряды ветра откидывают слабее (см.
 * BreezeWindChargeEntityMixin). Появляется только в цитадели - из её спавнеров и на её постах.
 */
public class PrismGaleEntity extends BreezeEntity {

	public static final double MAX_HEALTH = 18.0;

	public PrismGaleEntity(EntityType<? extends HostileEntity> entityType, World world) {
		super(entityType, world);
	}

	public static DefaultAttributeContainer.Builder createPrismGaleAttributes() {
		return BreezeEntity.createBreezeAttributes()
				.add(EntityAttributes.MOVEMENT_SPEED, 0.78)
				.add(EntityAttributes.MAX_HEALTH, MAX_HEALTH)
				.add(EntityAttributes.FOLLOW_RANGE, 28.0);
	}

	/** Залы цитадели освещены, а спавнер ванильно не выпускает враждебных мобов на свет - стражу можно. */
	@Override
	public boolean canSpawn(WorldAccess world, SpawnReason spawnReason) {
		return spawnReason == SpawnReason.SPAWNER || spawnReason == SpawnReason.STRUCTURE || super.canSpawn(world, spawnReason);
	}

	/** Стражи, сохранённые прежней версией с 36 здоровья, при загрузке получают новое значение. */
	@Override
	protected void readCustomData(ReadView view) {
		super.readCustomData(view);
		EntityAttributeInstance health = this.getAttributeInstance(EntityAttributes.MAX_HEALTH);
		if (health != null && health.getBaseValue() > MAX_HEALTH) {
			health.setBaseValue(MAX_HEALTH);
			this.setHealth(Math.min(this.getHealth(), (float) MAX_HEALTH));
		}
	}

	@Override
	public void tick() {
		super.tick();
		World world = this.getEntityWorld();
		if (world.isClient() && this.random.nextInt(6) == 0) {
			world.addParticleClient(ParticleTypes.GLOW,
					this.getParticleX(0.5), this.getRandomBodyY(), this.getParticleZ(0.5), 0.0, 0.02, 0.0);
		}
	}
}
