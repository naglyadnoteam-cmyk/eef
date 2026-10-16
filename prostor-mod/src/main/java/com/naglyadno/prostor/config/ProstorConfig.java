package com.naglyadno.prostor.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.naglyadno.prostor.ProstorMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Настройки мода в config/prostor.json. Частота биома читается один раз при запуске игры:
 * от неё зависит раскладка биомов, поэтому менять её стоит только перед созданием нового мира.
 */
public final class ProstorConfig {

	/** Как часто встречается биом. Ширина полосы влажности, которую биом занимает в своей климатической зоне. */
	public enum Frequency {
		OFF(0.0f), RARE(0.12f), NORMAL(0.2f), COMMON(0.3f);

		public final float humidityWidth;

		Frequency(float humidityWidth) {
			this.humidityWidth = humidityWidth;
		}

		static Frequency parse(String value, Frequency fallback) {
			if (value == null) {
				return fallback;
			}
			try {
				return valueOf(value.trim().toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				ProstorMod.LOGGER.warn("prostor.json: неизвестная частота '{}', используется {}", value,
						fallback.name().toLowerCase(Locale.ROOT));
				return fallback;
			}
		}
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Frequency lakeDistrict = Frequency.NORMAL;

	private ProstorConfig() {
	}

	public static Frequency lakeDistrict() {
		return lakeDistrict;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("prostor.json");
		JsonObject json = new JsonObject();
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				JsonObject read = GSON.fromJson(reader, JsonObject.class);
				if (read != null) {
					json = read;
				}
			} catch (IOException | RuntimeException e) {
				ProstorMod.LOGGER.warn("prostor.json не прочитан, используются настройки по умолчанию", e);
			}
		}
		lakeDistrict = Frequency.parse(json.has("lake_district") ? json.get("lake_district").getAsString() : null,
				Frequency.NORMAL);

		JsonObject out = new JsonObject();
		out.addProperty("_comment", "Частота биомов: off, rare, normal, common. Действует на новые чанки; лучше менять до создания мира.");
		out.addProperty("lake_district", lakeDistrict.name().toLowerCase(Locale.ROOT));
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(out, writer);
			}
		} catch (IOException e) {
			ProstorMod.LOGGER.warn("prostor.json не записан", e);
		}
		ProstorMod.LOGGER.info("Простор: частота Озёрного края = {}", lakeDistrict.name().toLowerCase(Locale.ROOT));
	}
}
