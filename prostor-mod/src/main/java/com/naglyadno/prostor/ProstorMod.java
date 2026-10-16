package com.naglyadno.prostor;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProstorMod implements ModInitializer {

	public static final String MOD_ID = "prostor";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Простор: мод загружен");
	}
}
