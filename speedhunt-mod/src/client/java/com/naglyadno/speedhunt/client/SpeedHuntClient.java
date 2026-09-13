package com.naglyadno.speedhunt.client;

import com.naglyadno.speedhunt.client.gui.SpeedHuntMenuScreen;
import com.naglyadno.speedhunt.network.GameStatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class SpeedHuntClient implements ClientModInitializer {

	private static KeyBinding openMenuKey;

	@Override
	public void onInitializeClient() {
		openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.speedhunt.open_menu",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_N,
				"category.speedhunt"
		));

		ClientPlayNetworking.registerGlobalReceiver(GameStatePayload.ID, (payload, context) ->
				context.client().execute(() -> ClientGameState.update(payload)));

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientGameState.clear());

		HudRenderCallback.EVENT.register((context, tickCounter) -> HudOverlay.render(context));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openMenuKey.wasPressed()) {
				if (client.currentScreen == null) {
					client.setScreen(new SpeedHuntMenuScreen());
				}
			}
		});
	}
}
