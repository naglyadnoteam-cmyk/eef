package com.naglyadno.nedra.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Настройки мода "Недра": пороги давления, редкость руд, тайминги опасностей. Хранится в config/nedra.json. */
public class NedraConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	// --- Давление ---
	/** Y, на котором давление ещё нулевое (уровень моря и выше). */
	public int surfaceY = 62;
	/**
	 * Y, на котором давление достигает максимума без снаряжения - новое дно мира.
	 * Ванильный бедрок был на -64; этот мод опускает нижнюю границу измерения ещё на 288
	 * блоков (кратно 16 - размеру чанк-секции), новое дно -352.
	 */
	public int deepY = -352;
	/** Каждый helmetReductionPercent% снижает "эффективную" глубину для расчёта давления. */
	public int helmetLightReductionPercent = 20;
	public int helmetReinforcedReductionPercent = 45;
	public int helmetDeepsuitReductionPercent = 75;
	/** С какого яруса (0-5) начинает действовать замедление добычи (Mining Fatigue). */
	public int miningFatigueStartTier = 2;
	/** С какого яруса начинается периодический урон без достаточной защиты. */
	public int damageStartTier = 5;
	public float damageAmount = 2.0f;
	public int damageIntervalTicks = 40;
	/** Насколько таблетка снижает давление (в процентных пунктах) и как долго. */
	public int tabletReductionPercent = 25;
	public int tabletDurationTicks = 20 * 60 * 3;
	/** Как часто (в тиках) пересчитывается и рассылается давление игрокам. */
	public int pressureUpdateIntervalTicks = 10;

	// --- Опасности ---
	public int rockfallCheckRadius = 6;
	public double rockfallChancePerCheck = 0.06;
	public int rockfallWarningDelayTicks = 30;
	public double currentPushStrength = 0.18;
	public int currentRadius = 5;

	// --- Звуковые руды ---
	public int echoOrePingIntervalTicks = 60;
	public double echoOreHearRadius = 16.0;
	public double geophoneHearRadius = 40.0;

	public static NedraConfig load(Path path) {
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				NedraConfig loaded = GSON.fromJson(reader, NedraConfig.class);
				if (loaded != null) {
					return loaded;
				}
			} catch (IOException | RuntimeException ignored) {
				// повреждённый файл - используем значения по умолчанию
			}
		}
		NedraConfig fresh = new NedraConfig();
		fresh.save(path);
		return fresh;
	}

	public void save(Path path) {
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException ignored) {
			// не критично, настройки просто не сохранятся в этот раз
		}
	}
}
