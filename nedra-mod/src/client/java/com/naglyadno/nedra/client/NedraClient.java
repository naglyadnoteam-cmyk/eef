package com.naglyadno.nedra.client;

import com.naglyadno.nedra.network.PressurePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class NedraClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(PressurePayload.ID, (payload, context) ->
				context.client().execute(() -> ClientPressureState.update(payload.pressure(), payload.tier())));

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientPressureState.clear());

		HudRenderCallback.EVENT.register((context, tickCounter) -> PressureHud.render(context));
	}
}
