package com.naglyadno.nedra.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.World;

/**
 * Обмороженный шахтёр - зомби, вмёрзший в лёд Замёрзших пещер. Удар промораживает: экран покрывается
 * инеем, как в рыхлом снегу, и на 3 секунды замедляет. Кожаная броня спасает от инея, как и в ванильной
 * игре. Сам не мёрзнет, не горит на солнце и не тонет в утопленника.
 */
public class FrostbittenEntity extends ZombieEntity {

	/** Сколько тиков заморозки добавляет удар (140 - полная заморозка с уроном холодом). */
	private static final int FREEZE_PER_HIT = 120;

	public FrostbittenEntity(EntityType<? extends FrostbittenEntity> entityType, World world) {
		super(entityType, world);
	}

	public static DefaultAttributeContainer.Builder createFrostbittenAttributes() {
		return ZombieEntity.createZombieAttributes()
				.add(EntityAttributes.MAX_HEALTH, 24.0)
				.add(EntityAttributes.ARMOR, 3.0)
				.add(EntityAttributes.MOVEMENT_SPEED, 0.21);
	}

	@Override
	protected boolean burnsInDaylight() {
		return false;
	}

	@Override
	protected boolean canConvertInWater() {
		return false;
	}

	@Override
	public boolean canFreeze() {
		return false;
	}

	@Override
	public boolean tryAttack(ServerWorld world, Entity target) {
		boolean hit = super.tryAttack(world, target);
		if (hit && target instanceof LivingEntity living && living.canFreeze()) {
			living.setFrozenTicks(Math.min(living.getFrozenTicks() + FREEZE_PER_HIT, 200));
			living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 0), this);
		}
		return hit;
	}

	@Override
	protected void initEquipment(Random random, LocalDifficulty localDifficulty) {
		// изредка - с ледорубом-лопатой, оставшейся от экспедиции
		if (random.nextFloat() < 0.12F) {
			this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SHOVEL));
		}
	}

	@Override
	public void tick() {
		super.tick();
		World world = this.getEntityWorld();
		if (world.isClient() && this.random.nextInt(18) == 0) {
			world.addParticleClient(ParticleTypes.SNOWFLAKE,
					this.getParticleX(0.5), this.getRandomBodyY(), this.getParticleZ(0.5), 0.0, -0.02, 0.0);
		}
	}
}
