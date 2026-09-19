package com.naglyadno.fatecards.client;

import com.naglyadno.fatecards.client.gui.FateCardsMenuScreen;
import com.naglyadno.fatecards.network.ChooseCardPayload;
import com.naglyadno.fatecards.network.FateStatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class FateCardsClient implements ClientModInitializer {

	private static KeyBinding openMenuKey;
	private static KeyBinding chooseCard1Key;
	private static KeyBinding chooseCard2Key;

	@Override
	public void onInitializeClient() {
		openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.fatecards.open_menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, "category.fatecards"));
		chooseCard1Key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.fatecards.choose_card_1", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_COMMA, "category.fatecards"));
		chooseCard2Key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.fatecards.choose_card_2", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_PERIOD, "category.fatecards"));

		ClientPlayNetworking.registerGlobalReceiver(FateStatePayload.ID, (payload, context) ->
				context.client().execute(() -> ClientFateState.update(payload)));

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientFateState.clear());

		HudRenderCallback.EVENT.register((context, tickCounter) -> HudOverlay.render(context));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openMenuKey.wasPressed()) {
				if (client.currentScreen == null) {
					client.setScreen(new FateCardsMenuScreen());
				}
			}

			FateStatePayload state = ClientFateState.latest();
			while (chooseCard1Key.wasPressed()) {
				if (state != null && state.offerPending()) {
					ClientPlayNetworking.send(new ChooseCardPayload(state.card1Id()));
				}
			}
			while (chooseCard2Key.wasPressed()) {
				if (state != null && state.offerPending()) {
					ClientPlayNetworking.send(new ChooseCardPayload(state.card2Id()));
				}
			}
		});
	}
}
