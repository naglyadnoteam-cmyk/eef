package com.naglyadno.nedra;

import com.naglyadno.nedra.block.ModBlocks;
import com.naglyadno.nedra.config.NedraConfig;
import com.naglyadno.nedra.effect.ModEffects;
import com.naglyadno.nedra.hazard.CurrentManager;
import com.naglyadno.nedra.hazard.RockfallManager;
import com.naglyadno.nedra.item.ModItems;
import com.naglyadno.nedra.network.PressurePayload;
import com.naglyadno.nedra.pressure.PressureManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class NedraMod implements ModInitializer {

	public static final String MOD_ID = "nedra";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static PressureManager pressureManager;
	private static RockfallManager rockfallManager;
	private static CurrentManager currentManager;
	private static final Set<UUID> GUIDE_GIVEN = new HashSet<>();

	@Override
	public void onInitialize() {
		ModEffects.init();
		ModBlocks.init();
		ModItems.init();
		com.naglyadno.nedra.item.ModItemGroup.init();
		com.naglyadno.nedra.worldgen.WorldGenInit.init();

		PayloadTypeRegistry.playS2C().register(PressurePayload.ID, PressurePayload.CODEC);

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			Path configPath = FabricLoader.getInstance().getConfigDir().resolve("nedra.json");
			NedraConfig config = NedraConfig.load(configPath);
			pressureManager = new PressureManager(server, config, configPath);
			rockfallManager = new RockfallManager(config);
			currentManager = new CurrentManager(config);
			LOGGER.info("Недра готовы: подземный мир пробуждается.");
		});

		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			pressureManager = null;
			rockfallManager = null;
			currentManager = null;
			GUIDE_GIVEN.clear();
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (pressureManager != null) {
				pressureManager.tick();
			}
			if (currentManager != null) {
				currentManager.tick(server);
			}
			if (rockfallManager != null) {
				rockfallManager.tick();
			}
		});

		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (!world.isClient && rockfallManager != null && player instanceof ServerPlayerEntity serverPlayer) {
				rockfallManager.onBlockBroken(serverPlayer, pos);
			}
		});

		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				maybeGiveGuideBook(handler.getPlayer()));

		LOGGER.info("Недра загружены.");
	}

	private void maybeGiveGuideBook(ServerPlayerEntity player) {
		if (!GUIDE_GIVEN.add(player.getUuid())) {
			return;
		}
		ItemStack book = com.naglyadno.nedra.guide.GuideBook.create();
		if (!player.getInventory().insertStack(book)) {
			player.dropItem(book, false);
		}
	}

	public static PressureManager pressureManager() {
		return pressureManager;
	}

	public static RockfallManager rockfallManager() {
		return rockfallManager;
	}

	public static NedraConfig config() {
		return pressureManager != null ? pressureManager.config() : new NedraConfig();
	}
}
