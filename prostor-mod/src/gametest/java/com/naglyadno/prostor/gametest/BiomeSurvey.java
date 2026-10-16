package com.naglyadno.prostor.gametest;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.TreeMap;

/**
 * Карта биомов вокруг спавна без генерации чанков: биом берётся прямо из источника биомов (шумов),
 * высота рельефа - из генератора. Печатает долю каждого биома в строках STATS|.
 */
final class BiomeSurvey {

	private static final Logger LOGGER = LoggerFactory.getLogger("prostor-gametest");
	/** Половина стороны квадрата обзора в блоках и шаг выборки. */
	private static final int HALF = 4096;
	private static final int STEP = 64;

	private BiomeSurvey() {
	}

	static void report(MinecraftServer server) {
		ServerWorld world = server.getOverworld();
		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		BiomeSource source = generator.getBiomeSource();
		NoiseConfig noise = world.getChunkManager().getNoiseConfig();
		MultiNoiseUtil.MultiNoiseSampler sampler = noise.getMultiNoiseSampler();
		Map<String, Integer> counts = new TreeMap<>();
		int total = 0;
		long start = System.currentTimeMillis();
		for (int x = -HALF; x <= HALF; x += STEP) {
			for (int z = -HALF; z <= HALF; z += STEP) {
				int y = generator.getHeight(x, z, Heightmap.Type.WORLD_SURFACE_WG, world, noise);
				RegistryEntry<Biome> biome = source.getBiome(x >> 2, Math.max(y, world.getSeaLevel()) >> 2, z >> 2, sampler);
				String id = biome.getKey().map(RegistryKey::getValue).map(Object::toString).orElse("?");
				counts.merge(id, 1, Integer::sum);
				total++;
			}
		}
		LOGGER.info("STATS| biome survey {}x{} blocks, step {}, {} samples, {} ms", 2 * HALF, 2 * HALF, STEP, total,
				System.currentTimeMillis() - start);
		final int all = total;
		int ours = counts.getOrDefault("prostor:lake_district", 0);
		LOGGER.info("STATS| lake_district share {}% ({} of {} samples)",
				String.format(java.util.Locale.ROOT, "%.2f", 100.0 * ours / all), ours, all);
		counts.entrySet().stream()
				.sorted((a, b) -> b.getValue() - a.getValue())
				.forEach(e -> LOGGER.info("STATS| biome {} {}%", e.getKey(),
						String.format(java.util.Locale.ROOT, "%.2f", 100.0 * e.getValue() / all)));
	}
}
