package com.naglyadno.nedra.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.naglyadno.nedra.NedraMod;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Настройки мода "Недра". Хранятся в config/nedra.json, перечитываются командой /nedra reload. */
public class NedraConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	// --- Давление ---
	/** Y, на котором давление ещё нулевое. */
	public int surfaceY = 62;
	/** Y, на котором давление достигает максимума без снаряжения (новое дно мира). */
	public int deepY = -352;
	public int helmetLightReductionPercent = 20;
	public int helmetReinforcedReductionPercent = 45;
	public int helmetDeepsuitReductionPercent = 75;
	/** С какого яруса (0-5) давление начинает замедлять добычу. */
	public int miningSlowdownStartTier = 2;
	/** На сколько (доля 0..1) замедляется добыча за каждый ярус начиная с порогового. */
	public double miningSlowdownPerTier = 0.12;
	/** С какого яруса начинается периодический урон. Урон никогда не опускает здоровье ниже 1. */
	public int damageStartTier = 5;
	public float damageAmount = 2.0f;
	public int damageIntervalTicks = 40;
	public int tabletReductionPercent = 25;
	public int tabletDurationTicks = 20 * 60 * 3;
	public int pressureUpdateIntervalTicks = 10;

	// --- Опасности ---
	public int rockfallCheckRadius = 6;
	public double rockfallChancePerCheck = 0.06;
	public int rockfallWarningDelayTicks = 40;
	/** Сила подброса воздушного разлома (прибавка вертикальной скорости за тик). */
	public double currentLiftStrength = 0.11;
	/** Высота столба воздуха над разломом, в которой он подхватывает игрока. */
	public int currentColumnHeight = 7;

	// --- Руды на слух ---
	public int geophoneRadius = 32;
	public int geophoneCooldownTicks = 40;

	// --- Магнетит ---
	public int magnetiteInterferenceRadius = 8;

	public static NedraConfig load(Path path) {
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				NedraConfig loaded = GSON.fromJson(reader, NedraConfig.class);
				if (loaded != null) {
					loaded.sanitize();
					// дописываем в файл поля, появившиеся в новых версиях мода
					loaded.save(path);
					return loaded;
				}
			} catch (IOException | RuntimeException e) {
				NedraMod.LOGGER.warn("config/nedra.json повреждён, используются значения по умолчанию: {}", e.toString());
			}
		}
		NedraConfig fresh = new NedraConfig();
		fresh.save(path);
		return fresh;
	}

	/** Копирует значения из свежезагруженного конфига в этот экземпляр (его держат менеджеры). */
	public void copyFrom(NedraConfig other) {
		for (Field field : NedraConfig.class.getDeclaredFields()) {
			if (Modifier.isStatic(field.getModifiers())) {
				continue;
			}
			try {
				field.set(this, field.get(other));
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	private void sanitize() {
		if (deepY >= surfaceY) {
			deepY = surfaceY - 1;
		}
		pressureUpdateIntervalTicks = Math.max(1, pressureUpdateIntervalTicks);
		damageIntervalTicks = Math.max(1, damageIntervalTicks);
		geophoneRadius = Math.max(4, Math.min(64, geophoneRadius));
		magnetiteInterferenceRadius = Math.max(1, Math.min(24, magnetiteInterferenceRadius));
		currentColumnHeight = Math.max(1, Math.min(16, currentColumnHeight));
		miningSlowdownPerTier = Math.max(0.0, Math.min(0.2, miningSlowdownPerTier));
	}

	public void save(Path path) {
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			NedraMod.LOGGER.warn("Не удалось сохранить config/nedra.json: {}", e.toString());
		}
	}
}
