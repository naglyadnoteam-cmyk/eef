package com.naglyadno.nedra.item;

import com.naglyadno.nedra.NedraMod;
import com.naglyadno.nedra.effect.ModEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * Таблетка от давления. Анимацию проглатывания и расход предмета даёт ванильный компонент
 * consumable; длительность эффекта берётся из серверного конфига в момент приёма.
 */
public class PressureTabletItem extends Item {

	public PressureTabletItem(Settings settings) {
		super(settings);
	}

	@Override
	public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
		if (!world.isClient()) {
			int duration = NedraMod.config().tabletDurationTicks;
			user.addStatusEffect(new StatusEffectInstance(ModEffects.PRESSURE_RESISTANCE, duration, 0, false, true, true));
		}
		return super.finishUsing(stack, world, user);
	}
}
