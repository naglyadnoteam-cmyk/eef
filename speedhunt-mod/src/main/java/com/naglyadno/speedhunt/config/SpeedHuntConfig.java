package com.naglyadno.speedhunt.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Настройки геймплея Speedhunt. Хранятся в config/speedhunt.json и
 * могут редактироваться вручную (сервер) или командами /speedhunt config.
 */
public class SpeedHuntConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Минимум игроков, чтобы можно было запустить матч (1 из них станет спидраннером, остальные — охотники). */
	public int minPlayers = 2;

	/** Через сколько секунд после старта игрокам показывается их роль. */
	public int roleRevealDelaySeconds = 3;

	/** Сколько секунд после показа ролей охотники не могут атаковать спидраннера. */
	public int graceProtectionSeconds = 5;

	/** Через сколько секунд после завершения матча происходит автосброс в лобби. */
	public int autoRestartDelaySeconds = 10;

	/** Телепортировать ли охотника рядом со спидраннером при возрождении. */
	public boolean teleportHunterOnRespawn = false;

	// --- Трекер (аналог компаса) ---
	/** Включить периодическую "подсветку" спидраннера для охотников (эффект Свечения + направление в HUD). */
	public boolean trackerEnabled = true;

	/** Раз в сколько секунд активируется трекер. */
	public int trackerIntervalSeconds = 45;

	/** На сколько секунд трекер остаётся активным. */
	public int trackerDurationSeconds = 6;

	// --- Бонусные ресурсы ---
	public boolean hunterBonusEnabled = true;
	public int hunterBonusGoldenApples = 1;
	public int hunterBonusIronIngots = 8;
	public int hunterBonusArrows = 16;

	public boolean speedrunnerBonusEnabled = true;
	public int speedrunnerBonusGoldenApples = 1;
	public int speedrunnerBonusEnderPearls = 2;

	public static SpeedHuntConfig loadOrCreate(Path path) {
		try {
			if (Files.exists(path)) {
				try (Reader reader = Files.newBufferedReader(path)) {
					SpeedHuntConfig loaded = GSON.fromJson(reader, SpeedHuntConfig.class);
					if (loaded != null) {
						return loaded;
					}
				}
			}
		} catch (IOException ignored) {
			// fall through to defaults
		}
		SpeedHuntConfig fresh = new SpeedHuntConfig();
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
			throw new RuntimeException("Не удалось сохранить конфиг Speedhunt: " + path, e);
		}
	}
}
