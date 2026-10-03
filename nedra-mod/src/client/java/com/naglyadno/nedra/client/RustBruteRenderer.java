package com.naglyadno.nedra.client;

import com.naglyadno.nedra.NedraMod;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.ZombieEntityRenderer;
import net.minecraft.client.render.entity.state.ZombieEntityRenderState;
import net.minecraft.util.Identifier;

/** Модель зомби со своей текстурой; увеличение даёт атрибут масштаба сущности. */
public class RustBruteRenderer extends ZombieEntityRenderer {

	private static final Identifier TEXTURE = Identifier.of(NedraMod.MOD_ID, "textures/entity/rust_brute.png");

	public RustBruteRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(ZombieEntityRenderState state) {
		return TEXTURE;
	}
}
