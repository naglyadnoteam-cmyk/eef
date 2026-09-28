package com.naglyadno.nedra.item;

import com.naglyadno.nedra.NedraMod;
import com.naglyadno.nedra.effect.ModEffects;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Таблетка от давления: сразу по нажатию ПКМ (без анимации еды - надёжнее между версиями)
 * даёт статус-эффект сопротивления давлению на время из конфига.
 */
public class PressureTabletItem extends Item {

	public PressureTabletItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, net.minecraft.entity.player.PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient) {
			int duration = NedraMod.config().tabletDurationTicks;
			user.addStatusEffect(new StatusEffectInstance(ModEffects.PRESSURE_RESISTANCE, duration, 0, false, true));
			world.playSound(null, user.getBlockPos(), SoundEvents.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS, 1.0f, 1.1f);
			if (!user.getAbilities().creativeMode) {
				stack.decrement(1);
			}
		}
		return TypedActionResult.success(stack, world.isClient);
	}
}
