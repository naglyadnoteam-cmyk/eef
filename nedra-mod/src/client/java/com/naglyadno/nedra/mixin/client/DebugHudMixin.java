package com.naglyadno.nedra.mixin.client;

import com.naglyadno.nedra.client.PressureHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Экран F3 рисуется после всех слоёв HUD, поэтому помехи давления накладываются прямо в его конце. */
@Mixin(DebugHud.class)
public abstract class DebugHudMixin {

	@Inject(method = "render", at = @At("TAIL"))
	private void nedra$pressureInterference(DrawContext context, CallbackInfo ci) {
		PressureHud.renderDebugInterference(context);
	}
}
