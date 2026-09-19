package com.naglyadno.fatecards.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Настройки Fate Cards. Хранятся в config/fatecards.json. */
public class FateCardsConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Минимум игроков онлайн, чтобы можно было запустить режим. */
	public int minPlayers = 2;

	/** Раз в сколько секунд разыгрывается новый раунд выбора карт (по умолчанию 5 минут). */
	public int roundIntervalSeconds = 300;

	/** Сколько секунд даётся игроку на выбор одной из двух карт, прежде чем выберется случайная. */
	public int decisionSeconds = 20;

	/** Задержка перед самым первым раундом после старта режима. */
	public int firstRoundDelaySeconds = 15;

	public static FateCardsConfig loadOrCreate(Path path) {
		try {
			if (Files.exists(path)) {
				try (Reader reader = Files.newBufferedReader(path)) {
					FateCardsConfig loaded = GSON.fromJson(reader, FateCardsConfig.class);
					if (loaded != null) {
						return loaded;
					}
				}
			}
		} catch (IOException ignored) {
			// fall through to defaults
		}
		FateCardsConfig fresh = new FateCardsConfig();
		fresh.save(path);
		return fresh;
	}

	public void save(Path path) {
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			throw new RuntimeException("Не удалось сохранить конфиг Fate Cards: " + path, e);
		}
	}
}
