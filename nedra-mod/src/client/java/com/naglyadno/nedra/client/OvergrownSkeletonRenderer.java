package com.naglyadno.nedra.client;

import com.naglyadno.nedra.NedraMod;
import com.naglyadno.nedra.entity.OvergrownSkeletonEntity;
import net.minecraft.client.render.entity.AbstractSkeletonEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.state.SkeletonEntityRenderState;
import net.minecraft.util.Identifier;

/** Модель обычного скелета с текстурой, обвитой лианами. */
public class OvergrownSkeletonRenderer extends AbstractSkeletonEntityRenderer<OvergrownSkeletonEntity, SkeletonEntityRenderState> {

	private static final Identifier TEXTURE = Identifier.of(NedraMod.MOD_ID, "textures/entity/overgrown_skeleton.png");

	public OvergrownSkeletonRenderer(EntityRendererFactory.Context context) {
		super(context, EntityModelLayers.SKELETON, EntityModelLayers.SKELETON_EQUIPMENT);
	}

	@Override
	public Identifier getTexture(SkeletonEntityRenderState state) {
		return TEXTURE;
	}

	@Override
	public SkeletonEntityRenderState createRenderState() {
		return new SkeletonEntityRenderState();
	}
}
