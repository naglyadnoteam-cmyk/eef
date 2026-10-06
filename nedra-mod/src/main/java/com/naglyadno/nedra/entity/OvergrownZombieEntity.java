package com.naglyadno.nedra.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

/**
 * Заросший зомби Заросших глубин: мох и лианы проросли сквозь него. Удар оставляет лёгкое отравление
 * спорами (2 секунды). Живёт в болоте, поэтому не тонет в утопленника, и не горит на солнце.
 */
public class OvergrownZombieEntity extends ZombieEntity {

	public OvergrownZombieEntity(EntityType<? extends OvergrownZombieEntity> entityType, World world) {
		super(entityType, world);
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
	public boolean tryAttack(ServerWorld world, Entity target) {
		boolean hit = super.tryAttack(world, target);
		if (hit && target instanceof LivingEntity living) {
			living.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 40, 0), this);
		}
		return hit;
	}

	@Override
	public void tick() {
		super.tick();
		World world = this.getEntityWorld();
		if (world.isClient() && this.random.nextInt(24) == 0) {
			world.addParticleClient(ParticleTypes.SPORE_BLOSSOM_AIR,
					this.getParticleX(0.5), this.getRandomBodyY(), this.getParticleZ(0.5), 0.0, 0.0, 0.0);
		}
	}
}
