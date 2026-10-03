package com.naglyadno.nedra.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.EntityType;
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
 * Ржавый громила - житель Магнитных пещер. Зомби-шахтёр, проржавевший в железистой породе: на треть
 * крупнее обычного зомби, медленнее и крепче, но бьёт почти так же. Удар тяжёлой рукой ненадолго
 * замедляет. Не горит на солнце, не тонет в зомби-утопленника, не бывает детёнышем и не зовёт подмогу.
 */
public class RustBruteEntity extends ZombieEntity {

	public RustBruteEntity(EntityType<? extends RustBruteEntity> entityType, World world) {
		super(entityType, world);
	}

	public static DefaultAttributeContainer.Builder createRustBruteAttributes() {
		return ZombieEntity.createZombieAttributes()
				.add(EntityAttributes.MAX_HEALTH, 30.0)
				.add(EntityAttributes.MOVEMENT_SPEED, 0.2)
				.add(EntityAttributes.ATTACK_DAMAGE, 4.0)
				.add(EntityAttributes.ARMOR, 4.0)
				.add(EntityAttributes.KNOCKBACK_RESISTANCE, 0.4)
				.add(EntityAttributes.SCALE, 1.3);
	}

	@Override
	protected void initAttributes() {
		// без случайного шанса вызвать подкрепление, который ванильный зомби задаёт здесь
		this.getAttributeInstance(EntityAttributes.SPAWN_REINFORCEMENTS).setBaseValue(0.0);
	}

	@Override
	public void setBaby(boolean baby) {
		super.setBaby(false);
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
	public float getSoundPitch() {
		// тот же голос зомби, но ниже: громила большой
		return super.getSoundPitch() * 0.7F;
	}

	@Override
	protected void initEquipment(Random random, LocalDifficulty localDifficulty) {
		// изредка - старая кирка из шахты
		if (random.nextFloat() < 0.1F) {
			this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
		}
	}

	@Override
	public boolean tryAttack(ServerWorld world, Entity target) {
		boolean hit = super.tryAttack(world, target);
		if (hit && target instanceof LivingEntity living) {
			living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 0), this);
		}
		return hit;
	}

	@Override
	public void tick() {
		super.tick();
		World world = this.getEntityWorld();
		// намагниченная ржавчина искрит
		if (world.isClient() && this.random.nextInt(12) == 0) {
			world.addParticleClient(ParticleTypes.ELECTRIC_SPARK,
					this.getParticleX(0.6), this.getRandomBodyY(), this.getParticleZ(0.6), 0.0, 0.0, 0.0);
		}
	}
}
