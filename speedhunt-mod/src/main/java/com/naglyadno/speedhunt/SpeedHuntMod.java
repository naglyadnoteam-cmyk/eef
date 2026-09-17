package com.naglyadno.speedhunt;

import com.naglyadno.speedhunt.command.SpeedHuntCommand;
import com.naglyadno.speedhunt.config.SpeedHuntConfig;
import com.naglyadno.speedhunt.game.GameManager;
import com.naglyadno.speedhunt.network.BannerPayload;
import com.naglyadno.speedhunt.network.GameStatePayload;
import com.naglyadno.speedhunt.network.RequestActionPayload;
import com.naglyadno.speedhunt.network.RequestCompassPayload;
import com.naglyadno.speedhunt.network.SetLoadoutPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import com.naglyadno.speedhunt.config.SpeedHuntConfig.LootEntry;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SpeedHuntMod implements ModInitializer {

	public static final String MOD_ID = "speedhunt";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static GameManager gameManager;

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.playS2C().register(GameStatePayload.ID, GameStatePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(BannerPayload.ID, BannerPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(RequestActionPayload.ID, RequestActionPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(RequestCompassPayload.ID, RequestCompassPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SetLoadoutPayload.ID, SetLoadoutPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(RequestActionPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					GameManager manager = gameManager;
					if (manager == null) {
						return;
					}
					Text result = payload.start() ? manager.start() : manager.stop();
					context.player().sendMessage(result, false);
				}));

		ServerPlayNetworking.registerGlobalReceiver(RequestCompassPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					GameManager manager = gameManager;
					if (manager != null) {
						manager.giveCompassOnRequest(context.player());
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(SetLoadoutPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					GameManager manager = gameManager;
					if (manager == null) {
						return;
					}
					List<LootEntry> entries = new ArrayList<>();
					for (int i = 0; i < payload.itemIds().size(); i++) {
						entries.add(new LootEntry(payload.itemIds().get(i), payload.counts().get(i)));
					}
					Text result = manager.setLoadout(payload.role(), entries);
					context.player().sendMessage(result, false);
				}));

		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
			if (gameManager != null) {
				gameManager.onPlayerChangeWorld(player, origin, destination);
			}
		});

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			Path configPath = FabricLoader.getInstance().getConfigDir().resolve("speedhunt.json");
			SpeedHuntConfig config = SpeedHuntConfig.loadOrCreate(configPath);
			gameManager = new GameManager(server, config, configPath);
			LOGGER.info("Speedhunt готов к игре.");
		});

		ServerLifecycleEvents.SERVER_STOPPED.register(server -> gameManager = null);

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (gameManager != null) {
				gameManager.tick();
			}
		});

		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (gameManager == null) {
				return true;
			}
			return gameManager.allowDamage(entity, source);
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (gameManager != null) {
				gameManager.onDeath(entity, source);
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (gameManager != null) {
				gameManager.onPlayerDisconnect(handler.getPlayer());
			}
		});

		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (gameManager != null) {
				gameManager.onRespawn(newPlayer);
			}
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				SpeedHuntCommand.register(dispatcher));

		LOGGER.info("Speedhunt (Speedrunner vs Hunter) загружен.");
	}

	public static GameManager getGameManager() {
		return gameManager;
	}
}
