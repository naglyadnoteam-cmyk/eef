package com.naglyadno.nedra.client;

import com.naglyadno.nedra.NedraMod;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.BreezeEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.LoadedEntityModels;
import net.minecraft.client.render.entity.state.BreezeEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Собственное свечение хрустального вихря: полупрозрачный синий слой поверх тела, который рисуется
 * с полной яркостью - страж остаётся синим и в тёмном зале, и под тёплым светом ламп.
 */
public class PrismGaleGlowFeatureRenderer extends FeatureRenderer<BreezeEntityRenderState, BreezeEntityModel> {

	private static final RenderLayer GLOW = RenderLayers.entityTranslucentEmissiveNoOutline(
			Identifier.of(NedraMod.MOD_ID, "textures/entity/prism_gale_glow.png"));
	private final BreezeEntityModel model;

	public PrismGaleGlowFeatureRenderer(FeatureRendererContext<BreezeEntityRenderState, BreezeEntityModel> context,
			LoadedEntityModels entityModels) {
		super(context);
		this.model = new BreezeEntityModel(entityModels.getModelPart(EntityModelLayers.BREEZE));
	}

	@Override
	public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, BreezeEntityRenderState state,
			float limbAngle, float limbDistance) {
		queue.getBatchingQueue(1).submitModel(this.model, state, matrices, GLOW, light, OverlayTexture.DEFAULT_UV, -1, null,
				state.outlineColor, null);
	}
}
