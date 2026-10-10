package com.naglyadno.nedra.client;

import com.naglyadno.nedra.NedraMod;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.ZombieEntityRenderer;
import net.minecraft.client.render.entity.state.ZombieEntityRenderState;
import net.minecraft.util.Identifier;

/** Модель зомби с обмороженной текстурой Замёрзших пещер. */
public class FrostbittenRenderer extends ZombieEntityRenderer {

	private static final Identifier TEXTURE = Identifier.of(NedraMod.MOD_ID, "textures/entity/frostbitten.png");

	public FrostbittenRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(ZombieEntityRenderState state) {
		return TEXTURE;
	}
}
