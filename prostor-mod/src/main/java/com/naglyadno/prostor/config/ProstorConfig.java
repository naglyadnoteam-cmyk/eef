package com.naglyadno.prostor.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.naglyadno.prostor.ProstorMod;
import com.naglyadno.prostor.worldgen.ProstorBiomes;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.biome.Biome;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Настройки мода в config/prostor.json: частота каждого биома. Читаются один раз при запуске игры:
 * от них зависит раскладка биомов, поэтому менять их стоит перед созданием нового мира.
 */
public final class ProstorConfig {

	/** Как часто встречается биом: множитель ширины климатической полосы, которую он занимает. */
	public enum Frequency {
		OFF(0.0f), RARE(0.6f), NORMAL(1.0f), COMMON(1.5f);

		public final float scale;

		Frequency(float scale) {
			this.scale = scale;
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
	private static final Map<RegistryKey<Biome>, Frequency> FREQUENCY = new HashMap<>();

	private ProstorConfig() {
	}

	public static Frequency frequency(RegistryKey<Biome> biome) {
		return FREQUENCY.getOrDefault(biome, Frequency.NORMAL);
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
		JsonObject out = new JsonObject();
		out.addProperty("_comment", "Частота биомов: off, rare, normal, common. Действует на новые чанки; лучше менять до создания мира.");
		StringBuilder log = new StringBuilder();
		for (RegistryKey<Biome> key : ProstorBiomes.ALL) {
			String name = key.getValue().getPath();
			Frequency f = Frequency.parse(json.has(name) ? json.get(name).getAsString() : null, Frequency.NORMAL);
			FREQUENCY.put(key, f);
			out.addProperty(name, f.name().toLowerCase(Locale.ROOT));
			log.append(name).append('=').append(f.name().toLowerCase(Locale.ROOT)).append(' ');
		}
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(out, writer);
			}
		} catch (IOException e) {
			ProstorMod.LOGGER.warn("prostor.json не записан", e);
		}
		ProstorMod.LOGGER.info("Простор: частоты биомов: {}", log.toString().trim());
	}
}
