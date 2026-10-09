package com.naglyadno.nedra.mixin;

import com.naglyadno.nedra.entity.PrismGaleEntity;
import net.minecraft.entity.projectile.BreezeWindChargeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Заряды ветра хрустального вихря слабее ванильных: сила взрыва 1.8 вместо 3.0 - меньше радиус,
 * и откидывает заметно слабее. Заряды обычного вихря не меняются.
 */
@Mixin(BreezeWindChargeEntity.class)
public abstract class BreezeWindChargeEntityMixin {

	@Unique
	private static final float PRISM_GALE_BURST_SCALE = 0.6F;

	@ModifyConstant(method = "createExplosion", constant = @Constant(floatValue = 3.0F))
	private float nedra$weakerPrismGaleBurst(float power) {
		return ((BreezeWindChargeEntity) (Object) this).getOwner() instanceof PrismGaleEntity ? power * PRISM_GALE_BURST_SCALE : power;
	}
}
