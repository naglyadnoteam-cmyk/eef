package com.naglyadno.fatecards;

import com.naglyadno.fatecards.command.FateCardsCommand;
import com.naglyadno.fatecards.config.FateCardsConfig;
import com.naglyadno.fatecards.game.GameManager;
import com.naglyadno.fatecards.network.ChooseCardPayload;
import com.naglyadno.fatecards.network.FateStatePayload;
import com.naglyadno.fatecards.network.RequestActionPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class FateCardsMod implements ModInitializer {

	public static final String MOD_ID = "fatecards";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static GameManager gameManager;

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.playS2C().register(FateStatePayload.ID, FateStatePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(RequestActionPayload.ID, RequestActionPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ChooseCardPayload.ID, ChooseCardPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(RequestActionPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					GameManager manager = gameManager;
					if (manager == null) {
						return;
					}
					Text result = payload.start() ? manager.start() : manager.stop();
					context.player().sendMessage(result, false);
				}));

		ServerPlayNetworking.registerGlobalReceiver(ChooseCardPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					GameManager manager = gameManager;
					if (manager != null) {
						manager.chooseCard(context.player(), payload.cardId());
					}
				}));

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			Path configPath = FabricLoader.getInstance().getConfigDir().resolve("fatecards.json");
			FateCardsConfig config = FateCardsConfig.loadOrCreate(configPath);
			gameManager = new GameManager(server, config, configPath);
			LOGGER.info("Fate Cards готов к игре.");
		});

		ServerLifecycleEvents.SERVER_STOPPED.register(server -> gameManager = null);

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (gameManager != null) {
				gameManager.tick();
			}
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				FateCardsCommand.register(dispatcher));

		LOGGER.info("Fate Cards загружен.");
	}

	public static GameManager getGameManager() {
		return gameManager;
	}
}
