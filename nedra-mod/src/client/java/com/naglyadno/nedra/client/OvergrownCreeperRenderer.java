package com.naglyadno.nedra.client;

import com.naglyadno.nedra.NedraMod;
import net.minecraft.client.render.entity.CreeperEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.CreeperEntityRenderState;
import net.minecraft.util.Identifier;

/** Модель крипера (с ванильной заряженной оболочкой) и мшистой текстурой. */
public class OvergrownCreeperRenderer extends CreeperEntityRenderer {

	private static final Identifier TEXTURE = Identifier.of(NedraMod.MOD_ID, "textures/entity/overgrown_creeper.png");

	public OvergrownCreeperRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(CreeperEntityRenderState state) {
		return TEXTURE;
	}
}
