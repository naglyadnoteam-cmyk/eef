package com.naglyadno.prostor.worldgen;

import com.mojang.datafixers.util.Pair;
import com.naglyadno.prostor.config.ProstorConfig;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.biome.source.util.MultiNoiseUtil.ParameterRange;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Встраивает биомы мода в ванильную раскладку верхнего мира.
 *
 * <p>Ванилла описывает каждый биом набором диапазонов шести шумов (температура, влажность,
 * континентальность, эрозия, «странность», глубина). Мод не добавляет новые области, а забирает
 * у подходящего ванильного биома часть его диапазона: так новый биом всегда оказывается в том же
 * климате и на той же суше, что и «хозяин», и никогда - в океане, во льдах или в пустыне.
 */
public final class BiomePlacement {

	/** Зона Озёрного края: прохладный пояс (как у тайги и обычного леса), только суша вглубь от берега, холмистый рельеф. */
	private static final ParameterRange LAKE_TEMPERATURE = ParameterRange.of(-0.45f, -0.15f);
	private static final ParameterRange LAKE_CONTINENTALNESS = ParameterRange.of(-0.11f, 1.0f);
	private static final ParameterRange LAKE_EROSION = ParameterRange.of(-0.375f, 0.45f);
	/**
	 * Середина полосы влажности - чуть суше границы леса и тайги (0.1): озёрный край лежит на стыке двух
	 * лесов и забирает в основном площадь обычного леса, а редкую тайгу почти не трогает.
	 */
	private static final float LAKE_HUMIDITY_CENTER = 0.06f;
	private static final Set<RegistryKey<Biome>> LAKE_HOSTS = Set.of(BiomeKeys.FOREST, BiomeKeys.TAIGA);

	private BiomePlacement() {
	}

	/**
	 * Вызывается вместо ванильной записи одного биома. Возвращает true, если запись разделена и уже
	 * отправлена в consumer (ванильную запись тогда нужно пропустить).
	 */
	public static boolean split(Consumer<Pair<MultiNoiseUtil.NoiseHypercube, RegistryKey<Biome>>> parameters,
			ParameterRange temperature, ParameterRange humidity, ParameterRange continentalness,
			ParameterRange erosion, ParameterRange weirdness, float offset, RegistryKey<Biome> biome) {
		ProstorConfig.Frequency frequency = ProstorConfig.lakeDistrict();
		if (frequency == ProstorConfig.Frequency.OFF || !LAKE_HOSTS.contains(biome)
				|| !contains(LAKE_TEMPERATURE, temperature) || !contains(LAKE_EROSION, erosion)) {
			return false;
		}
		float half = frequency.humidityWidth / 2.0f;
		ParameterRange band = ParameterRange.of(LAKE_HUMIDITY_CENTER - half, LAKE_HUMIDITY_CENTER + half);
		ParameterRange ourHumidity = intersect(humidity, band);
		ParameterRange ourContinentalness = intersect(continentalness, LAKE_CONTINENTALNESS);
		if (ourHumidity == null || ourContinentalness == null) {
			return false;
		}
		// наша часть
		write(parameters, temperature, ourHumidity, ourContinentalness, erosion, weirdness, offset, ProstorBiomes.LAKE_DISTRICT);
		// остальное остаётся хозяину: влажность ниже и выше полосы (во всю ширину континентальности)...
		if (humidity.min() < ourHumidity.min()) {
			write(parameters, temperature, new ParameterRange(humidity.min(), ourHumidity.min()), continentalness,
					erosion, weirdness, offset, biome);
		}
		if (humidity.max() > ourHumidity.max()) {
			write(parameters, temperature, new ParameterRange(ourHumidity.max(), humidity.max()), continentalness,
					erosion, weirdness, offset, biome);
		}
		// ...и прибрежная часть внутри полосы
		if (continentalness.min() < ourContinentalness.min()) {
			write(parameters, temperature, ourHumidity, new ParameterRange(continentalness.min(), ourContinentalness.min()),
					erosion, weirdness, offset, biome);
		}
		if (continentalness.max() > ourContinentalness.max()) {
			write(parameters, temperature, ourHumidity, new ParameterRange(ourContinentalness.max(), continentalness.max()),
					erosion, weirdness, offset, biome);
		}
		return true;
	}

	/** Точная копия ванильной записи: поверхность (глубина 0) и подземелье под ней (глубина 1). */
	private static void write(Consumer<Pair<MultiNoiseUtil.NoiseHypercube, RegistryKey<Biome>>> parameters,
			ParameterRange temperature, ParameterRange humidity, ParameterRange continentalness,
			ParameterRange erosion, ParameterRange weirdness, float offset, RegistryKey<Biome> biome) {
		parameters.accept(Pair.of(MultiNoiseUtil.createNoiseHypercube(temperature, humidity, continentalness, erosion,
				ParameterRange.of(0.0f), weirdness, offset), biome));
		parameters.accept(Pair.of(MultiNoiseUtil.createNoiseHypercube(temperature, humidity, continentalness, erosion,
				ParameterRange.of(1.0f), weirdness, offset), biome));
	}

	private static boolean contains(ParameterRange outer, ParameterRange inner) {
		return inner.min() >= outer.min() && inner.max() <= outer.max();
	}

	private static ParameterRange intersect(ParameterRange a, ParameterRange b) {
		long min = Math.max(a.min(), b.min());
		long max = Math.min(a.max(), b.max());
		return min < max ? new ParameterRange(min, max) : null;
	}
}
