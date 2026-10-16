package com.naglyadno.prostor.client;

import com.naglyadno.prostor.block.ModBlocks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.BlockRenderLayers;

public class ProstorClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		// растения с прозрачными пикселями рисуются в том же слое, что и ванильный тростник
		BlockRenderLayerMap.putBlocks(BlockRenderLayers.getBlockLayer(Blocks.SUGAR_CANE.getDefaultState()), ModBlocks.REEDS);
	}
}
