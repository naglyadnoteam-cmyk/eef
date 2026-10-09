package com.naglyadno.nedra;

import com.naglyadno.nedra.block.ModBlocks;
import com.naglyadno.nedra.command.NedraCommands;
import com.naglyadno.nedra.component.ModComponents;
import com.naglyadno.nedra.config.NedraConfig;
import com.naglyadno.nedra.effect.ModEffects;
import com.naglyadno.nedra.entity.ModEntities;
import com.naglyadno.nedra.guide.GuideBook;
import com.naglyadno.nedra.hazard.CurrentManager;
import com.naglyadno.nedra.hazard.RiverCurrentManager;
import com.naglyadno.nedra.hazard.MagnetiteInterferenceManager;
import com.naglyadno.nedra.hazard.RockfallManager;
import com.naglyadno.nedra.item.ModItemGroup;
import com.naglyadno.nedra.item.ModItems;
import com.naglyadno.nedra.network.ConfigSyncPayload;
import com.naglyadno.nedra.network.PressurePayload;
import com.naglyadno.nedra.network.RiverFlowPayload;
import com.naglyadno.nedra.pressure.PressureManager;
import com.naglyadno.nedra.sound.ModSounds;
import com.naglyadno.nedra.util.ServerScheduler;
import com.naglyadno.nedra.worldgen.BiomePainter;
import com.naglyadno.nedra.worldgen.WorldGenInit;
import com.naglyadno.nedra.worldgen.feature.ModFeatures;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.registry.CompostingChanceRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class NedraMod implements ModInitializer {

	public static final String MOD_ID = "nedra";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Тег игрока: справочник уже выдан. Хранится в данных игрока, поэтому переживает рестарты. */
	private static final String GUIDE_TAG = "nedra.guide_received";

	private static final NedraConfig CONFIG = new NedraConfig();
	private static PressureManager pressureManager;
	private static RockfallManager rockfallManager;
	private static CurrentManager currentManager;
	private static RiverCurrentManager riverManager;
	private static MagnetiteInterferenceManager magnetiteManager;

	@Override
	public void onInitialize() {
		ModSounds.init();
		ModComponents.init();
		ModEffects.init();
		ModBlocks.init();
		ModEntities.init();
		ModItems.init();
		ModItemGroup.init();
		ModFeatures.init();
		WorldGenInit.init();

		CompostingChanceRegistry.INSTANCE.add(ModItems.DEEPMOSS_CLUMP, 0.5f);
		CompostingChanceRegistry.INSTANCE.add(ModBlocks.DEEPMOSS, 0.65f);

		PayloadTypeRegistry.playS2C().register(PressurePayload.ID, PressurePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ConfigSyncPayload.ID, ConfigSyncPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(RiverFlowPayload.ID, RiverFlowPayload.CODEC);

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				NedraCommands.register(dispatcher));

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			CONFIG.copyFrom(NedraConfig.load(configPath()));
			pressureManager = new PressureManager(server, CONFIG);
			rockfallManager = new RockfallManager(CONFIG);
			currentManager = new CurrentManager(CONFIG);
			riverManager = new RiverCurrentManager();
			magnetiteManager = new MagnetiteInterferenceManager(CONFIG);
			LOGGER.info("Недра готовы: подземный мир пробуждается.");
		});

		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			pressureManager = null;
			rockfallManager = null;
			currentManager = null;
			riverManager = null;
			magnetiteManager = null;
			ServerScheduler.clear();
			BiomePainter.clear();
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ServerScheduler.tick();
			if (pressureManager != null) {
				pressureManager.tick();
				currentManager.tick(server);
				riverManager.tick(server);
				rockfallManager.tick();
				magnetiteManager.tick(server);
			}
			BiomePainter.tick(server.getOverworld());
		});

		ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
			if (world.getRegistryKey() == World.OVERWORLD) {
				BiomePainter.onChunkLoad(world, chunk);
			}
		});

		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (rockfallManager != null && player instanceof ServerPlayerEntity serverPlayer) {
				rockfallManager.onBlockBroken(serverPlayer, pos);
			}
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.getPlayer();
			ServerPlayNetworking.send(player, ConfigSyncPayload.of(CONFIG));
			giveGuideOnce(player);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (pressureManager != null) {
				pressureManager.onDisconnect(handler.getPlayer());
				riverManager.onDisconnect(handler.getPlayer());
			}
		});

		LOGGER.info("Недра загружены.");
	}

	private static void giveGuideOnce(ServerPlayerEntity player) {
		if (player.getCommandTags().contains(GUIDE_TAG)) {
			return;
		}
		player.addCommandTag(GUIDE_TAG);
		ItemStack book = GuideBook.create();
		if (!player.getInventory().insertStack(book)) {
			player.dropItem(book, false);
		}
	}

	private static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("nedra.json");
	}

	public static void reloadConfig(MinecraftServer server) {
		CONFIG.copyFrom(NedraConfig.load(configPath()));
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			ServerPlayNetworking.send(player, ConfigSyncPayload.of(CONFIG));
		}
	}

	public static PressureManager pressureManager() {
		return pressureManager;
	}

	public static NedraConfig config() {
		return CONFIG;
	}
}
