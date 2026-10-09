package com.naglyadno.nedra.client;

import com.naglyadno.nedra.NedraMod;
import net.minecraft.client.render.entity.BreezeEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.BreezeEntityRenderState;
import net.minecraft.util.Identifier;

/** Модель ванильного вихря (с вихрем ветра и глазами), синяя хрустальная текстура и собственное свечение. */
public class PrismGaleRenderer extends BreezeEntityRenderer {

	private static final Identifier TEXTURE = Identifier.of(NedraMod.MOD_ID, "textures/entity/prism_gale.png");

	public PrismGaleRenderer(EntityRendererFactory.Context context) {
		super(context);
		this.addFeature(new PrismGaleGlowFeatureRenderer(this, context.getEntityModels()));
	}

	@Override
	public Identifier getTexture(BreezeEntityRenderState state) {
		return TEXTURE;
	}
}
