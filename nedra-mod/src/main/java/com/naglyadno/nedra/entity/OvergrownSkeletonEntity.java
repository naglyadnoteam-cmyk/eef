package com.naglyadno.nedra.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.jspecify.annotations.Nullable;

/**
 * Заросший скелет: кости обвиты лианами. Его стрелы опутывают - замедление на 3 секунды. Как и все
 * жители недр, не горит на солнце; в отличие от обычного скелета не превращается в зимогора.
 */
public class OvergrownSkeletonEntity extends SkeletonEntity {

	public OvergrownSkeletonEntity(EntityType<? extends OvergrownSkeletonEntity> entityType, World world) {
		super(entityType, world);
	}

	/** Обычный скелет в рыхлом снегу начинает превращаться в зимогора; заросший - нет. */
	@Override
	public void setConversionTime(int time) {
	}

	@Override
	protected PersistentProjectileEntity createArrowProjectile(ItemStack arrow, float damageModifier, @Nullable ItemStack shotFrom) {
		PersistentProjectileEntity projectile = super.createArrowProjectile(arrow, damageModifier, shotFrom);
		if (projectile instanceof ArrowEntity arrowEntity) {
			arrowEntity.addEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 0));
		}
		return projectile;
	}
}
