package com.naglyadno.nedra.client;

import com.naglyadno.nedra.NedraMod;
import com.naglyadno.nedra.block.ModBlocks;
import com.naglyadno.nedra.entity.ModEntities;
import com.naglyadno.nedra.network.ConfigSyncPayload;
import com.naglyadno.nedra.network.PressurePayload;
import com.naglyadno.nedra.network.RiverFlowPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.BlockRenderLayers;
import net.minecraft.util.Identifier;

public class NedraClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(PressurePayload.ID, (payload, context) ->
				context.client().execute(() -> ClientPressureState.update(payload)));
		ClientPlayNetworking.registerGlobalReceiver(ConfigSyncPayload.ID, (payload, context) ->
				context.client().execute(() -> ClientPressureState.updateConfig(payload)));
		ClientPlayNetworking.registerGlobalReceiver(RiverFlowPayload.ID, (payload, context) ->
				context.client().execute(() -> ClientRiverFlow.update(payload)));

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientPressureState.clear();
			ClientAmbience.reset();
			ClientRiverFlow.reset();
		});

		ClientTickEvents.END_CLIENT_TICK.register(ClientAmbience::tick);
		ClientTickEvents.END_CLIENT_TICK.register(ClientRiverFlow::tick);

		// виньетка - под остальным интерфейсом (рядом с ванильными оверлеями), манометр - поверх
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
				Identifier.of(NedraMod.MOD_ID, "pressure_vignette"), (context, tickCounter) -> PressureHud.renderVignette(context));
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
				Identifier.of(NedraMod.MOD_ID, "pressure_gauge"), (context, tickCounter) -> PressureHud.renderGauge(context));

		EntityRendererRegistry.register(ModEntities.RUST_BRUTE, RustBruteRenderer::new);
		EntityRendererRegistry.register(ModEntities.OVERGROWN_ZOMBIE, OvergrownZombieRenderer::new);
		EntityRendererRegistry.register(ModEntities.OVERGROWN_SKELETON, OvergrownSkeletonRenderer::new);
		EntityRendererRegistry.register(ModEntities.OVERGROWN_CREEPER, OvergrownCreeperRenderer::new);
		EntityRendererRegistry.register(ModEntities.PRISM_GALE, PrismGaleRenderer::new);

		// растения с прозрачными пикселями рисуются в том же слое, что и ванильные свисающие корни
		BlockRenderLayerMap.putBlocks(BlockRenderLayers.getBlockLayer(Blocks.HANGING_ROOTS.getDefaultState()),
				ModBlocks.DEEP_VINE, ModBlocks.DEEP_FERN, ModBlocks.GLOWCAP, ModBlocks.SCARLET_CLUSTER);

		ItemTooltipCallback.EVENT.register((stack, tooltipContext, type, lines) -> NedraTooltips.append(stack, lines));
	}
}
