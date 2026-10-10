package com.naglyadno.nedra.mixin.client;

import com.naglyadno.nedra.client.ClientFrostLight;
import net.minecraft.client.render.LightmapTextureManager;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Передаёт в шейдер карты освещения силу холодного света Замёрзших пещер (см. {@link ClientFrostLight}). */
@Mixin(LightmapTextureManager.class)
public abstract class LightmapTextureManagerMixin {

	@ModifyVariable(method = "update", at = @At("STORE"), ordinal = 0)
	private Vector3f nedra$frostAmbient(Vector3f color) {
		return ClientFrostLight.ambientColor(color);
	}
}
