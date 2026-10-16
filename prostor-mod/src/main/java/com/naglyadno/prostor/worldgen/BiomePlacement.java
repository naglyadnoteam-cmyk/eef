package com.naglyadno.prostor.worldgen;

import com.mojang.datafixers.util.Pair;
import com.naglyadno.prostor.config.ProstorConfig;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.biome.source.util.MultiNoiseUtil.ParameterRange;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Встраивает биомы мода в ванильную раскладку верхнего мира.
 *
 * <p>Ванилла описывает каждый биом набором диапазонов шести шумов (температура, влажность,
 * континентальность, эрозия, «странность», глубина). Мод не добавляет новые области, а забирает
 * у подходящего ванильного биома («хозяина») часть его диапазона - пересечение с климатической коробкой
 * своего биома. Поэтому новый биом всегда оказывается в том же климате и на той же суше, что и хозяин:
 * река - на месте реки, болото - на месте болота, и никогда - в океане, во льдах или в пустыне.
 */
public final class BiomePlacement {

	/**
	 * Правило одного биома: хозяева, коробка по температуре, континентальности и эрозии и полоса влажности
	 * (её ширина умножается на частоту из настроек).
	 */
	private record Rule(RegistryKey<Biome> biome, Set<RegistryKey<Biome>> hosts, ParameterRange temperature,
			ParameterRange continentalness, ParameterRange erosion, float humidityCenter, float humidityWidth) {

		ParameterRange humidity() {
			float half = humidityWidth * ProstorConfig.frequency(biome).scale / 2.0f;
			return ParameterRange.of(Math.max(-1.0f, humidityCenter - half), Math.min(1.0f, humidityCenter + half));
		}
	}

	private static final ParameterRange T_COOL = ParameterRange.of(-0.45f, -0.15f);
	private static final ParameterRange T_TEMPERATE = ParameterRange.of(-0.15f, 0.2f);
	private static final ParameterRange T_COOL_TO_TEMPERATE = ParameterRange.of(-0.45f, 0.2f);
	private static final ParameterRange T_TEMPERATE_TO_WARM = ParameterRange.of(-0.15f, 0.55f);
	private static final ParameterRange INLAND = ParameterRange.of(-0.11f, 1.0f);
	private static final ParameterRange MID_INLAND = ParameterRange.of(0.03f, 1.0f);

	private static final List<Rule> RULES = List.of(
			// Озёрный край: прохладный холмистый край на стыке леса и тайги
			new Rule(ProstorBiomes.LAKE_DISTRICT, Set.of(BiomeKeys.FOREST, BiomeKeys.TAIGA), T_COOL, INLAND,
					ParameterRange.of(-0.375f, 0.45f), 0.06f, 0.16f),
			// Широкие реки с поймами: реки умеренного пояса вдали от моря, на равнинах
			new Rule(ProstorBiomes.FLOODPLAIN, Set.of(BiomeKeys.RIVER), T_COOL_TO_TEMPERATE, MID_INLAND,
					ParameterRange.of(0.05f, 0.55f), 0.35f, 1.3f),
			// Речные каньоны: высокая холмистая суша умеренного и тёплого пояса, сухая сторона
			new Rule(ProstorBiomes.RIVER_CANYON, Set.of(BiomeKeys.PLAINS, BiomeKeys.FOREST, BiomeKeys.SAVANNA,
					BiomeKeys.MEADOW, BiomeKeys.SAVANNA_PLATEAU), T_TEMPERATE_TO_WARM, MID_INLAND,
					ParameterRange.of(-0.78f, -0.2225f), -0.2f, 0.3f),
			// Гигантские водопады: крутые предгорья прохладного и умеренного пояса, влажная сторона
			new Rule(ProstorBiomes.WATERFALL_CLIFFS, Set.of(BiomeKeys.FOREST, BiomeKeys.TAIGA, BiomeKeys.MEADOW,
					BiomeKeys.PLAINS, BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA, BiomeKeys.BIRCH_FOREST, BiomeKeys.OLD_GROWTH_BIRCH_FOREST),
					T_COOL_TO_TEMPERATE, MID_INLAND, ParameterRange.of(-0.78f, -0.2225f), 0.3f, 0.6f),
			// Болотистые низины: вся прохладная часть ванильного болота
			new Rule(ProstorBiomes.MARSH, Set.of(BiomeKeys.SWAMP), T_COOL, INLAND,
					ParameterRange.of(0.45f, 1.0f), 0.0f, 2.0f));

	private BiomePlacement() {
	}

	/**
	 * Вызывается вместо ванильной записи одного биома. Возвращает true, если запись разделена и уже
	 * отправлена в consumer (ванильную запись тогда нужно пропустить).
	 */
	public static boolean split(Consumer<Pair<MultiNoiseUtil.NoiseHypercube, RegistryKey<Biome>>> parameters,
			ParameterRange temperature, ParameterRange humidity, ParameterRange continentalness,
			ParameterRange erosion, ParameterRange weirdness, float offset, RegistryKey<Biome> biome) {
		for (Rule rule : RULES) {
			if (rule.hosts().contains(biome) && ProstorConfig.frequency(rule.biome()) != ProstorConfig.Frequency.OFF
					&& overlaps(rule, temperature, humidity, continentalness, erosion)) {
				apply(parameters, 0, new ParameterRange[]{temperature, humidity, continentalness, erosion}, weirdness, offset, biome);
				return true;
			}
		}
		return false;
	}

	private static boolean overlaps(Rule rule, ParameterRange t, ParameterRange h, ParameterRange c, ParameterRange e) {
		return intersect(t, rule.temperature()) != null && intersect(h, rule.humidity()) != null
				&& intersect(c, rule.continentalness()) != null && intersect(e, rule.erosion()) != null;
	}

	/**
	 * Разрезает коробку entry (температура, влажность, континентальность, эрозия) по правилам начиная с ruleIndex:
	 * пересечение с коробкой правила уходит его биому, остальные куски - хозяину (и проверяются следующими правилами).
	 */
	private static void apply(Consumer<Pair<MultiNoiseUtil.NoiseHypercube, RegistryKey<Biome>>> parameters, int ruleIndex,
			ParameterRange[] box, ParameterRange weirdness, float offset, RegistryKey<Biome> host) {
		for (int i = ruleIndex; i < RULES.size(); i++) {
			Rule rule = RULES.get(i);
			if (!rule.hosts().contains(host) || ProstorConfig.frequency(rule.biome()) == ProstorConfig.Frequency.OFF) {
				continue;
			}
			ParameterRange[] ruleBox = {rule.temperature(), rule.humidity(), rule.continentalness(), rule.erosion()};
			ParameterRange[] inside = box.clone();
			boolean overlap = true;
			for (int d = 0; d < 4 && overlap; d++) {
				inside[d] = intersect(box[d], ruleBox[d]);
				overlap = inside[d] != null;
			}
			if (!overlap) {
				continue;
			}
			// куски вне коробки правила: по каждой оси - ниже и выше, при уже суженных предыдущих осях
			ParameterRange[] rest = box.clone();
			for (int d = 0; d < 4; d++) {
				if (box[d].min() < inside[d].min()) {
					ParameterRange[] piece = rest.clone();
					piece[d] = new ParameterRange(box[d].min(), inside[d].min());
					apply(parameters, i + 1, piece, weirdness, offset, host);
				}
				if (box[d].max() > inside[d].max()) {
					ParameterRange[] piece = rest.clone();
					piece[d] = new ParameterRange(inside[d].max(), box[d].max());
					apply(parameters, i + 1, piece, weirdness, offset, host);
				}
				rest[d] = inside[d];
			}
			write(parameters, inside, weirdness, offset, rule.biome());
			return;
		}
		write(parameters, box, weirdness, offset, host);
	}

	/** Точная копия ванильной записи: поверхность (глубина 0) и подземелье под ней (глубина 1). */
	private static void write(Consumer<Pair<MultiNoiseUtil.NoiseHypercube, RegistryKey<Biome>>> parameters,
			ParameterRange[] box, ParameterRange weirdness, float offset, RegistryKey<Biome> biome) {
		parameters.accept(Pair.of(MultiNoiseUtil.createNoiseHypercube(box[0], box[1], box[2], box[3],
				ParameterRange.of(0.0f), weirdness, offset), biome));
		parameters.accept(Pair.of(MultiNoiseUtil.createNoiseHypercube(box[0], box[1], box[2], box[3],
				ParameterRange.of(1.0f), weirdness, offset), biome));
	}

	private static ParameterRange intersect(ParameterRange a, ParameterRange b) {
		long min = Math.max(a.min(), b.min());
		long max = Math.min(a.max(), b.max());
		return min < max ? new ParameterRange(min, max) : null;
	}
}
