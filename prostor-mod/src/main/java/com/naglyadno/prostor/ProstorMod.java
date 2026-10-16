package com.naglyadno.prostor;

import com.naglyadno.prostor.block.ModBlocks;
import com.naglyadno.prostor.command.ProstorCommands;
import com.naglyadno.prostor.config.ProstorConfig;
import com.naglyadno.prostor.worldgen.ModFeatures;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProstorMod implements ModInitializer {

	public static final String MOD_ID = "prostor";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// настройки нужны до того, как игра впервые построит раскладку биомов
		ProstorConfig.load();
		ModBlocks.init();
		ModFeatures.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				ProstorCommands.register(dispatcher));
		LOGGER.info("Простор: мод загружен");
	}
}
