package com.naglyadno.prostor.worldgen;

import net.minecraft.registry.RegistryKey;
import net.minecraft.world.biome.Biome;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * «Насколько здесь биом»: доля точек вокруг столбца (сетка 3x3 через 8 блоков), где по шумам стоит
 * нужный биом. 1 - глубоко внутри, 0 - снаружи, между ними - плавный край. Считается только из шумов,
 * поэтому одинаково в любом чанке; переход рельефа к соседнему биому получается без ступенек.
 */
public final class BiomeFactor {

	/** Высоты поверхности по шумам в узлах сетки 4x4 (дорогой расчёт, поэтому с кэшем). */
	private static final Map<Long, Integer> SURFACE = new LinkedHashMap<>(8192, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, Integer> eldest) {
			return size() > 65536;
		}
	};

	private BiomeFactor() {
	}

	/** Множитель для 16x16 столбцов чанка с началом (bx, bz): индекс [(x - bx) * 16 + (z - bz)]. */
	public static double[] chunk(Lakes.Terrain terrain, RegistryKey<Biome> key, int bx, int bz) {
		// узлы сетки через 4 блока от bx-8 до bx+24
		int n = 9;
		boolean[] in = new boolean[n * n];
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				int x = bx - 8 + i * 4;
				int z = bz - 8 + j * 4;
				in[i * n + j] = terrain.isBiome(key, x, surface(terrain, x, z), z);
			}
		}
		double[] result = new double[256];
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				// ближайший узел к столбцу и узлы через 8 блоков (2 шага сетки) вокруг него
				int ci = (dx + 2) / 4 + 2;
				int cj = (dz + 2) / 4 + 2;
				int count = 0;
				for (int oi = -2; oi <= 2; oi += 2) {
					for (int oj = -2; oj <= 2; oj += 2) {
						if (in[(ci + oi) * n + (cj + oj)]) {
							count++;
						}
					}
				}
				result[dx * 16 + dz] = count / 9.0;
			}
		}
		return result;
	}

	/** Сглаженный множитель: 0 у края, 1 внутри, плавный переход. */
	public static double smooth(double f) {
		double t = Math.max(0, Math.min(1, (f - 0.1) / 0.8));
		return t * t * (3 - 2 * t);
	}

	private static int surface(Lakes.Terrain terrain, int x, int z) {
		long key = ((long) x << 32) ^ (z & 0xFFFFFFFFL) ^ (terrain.seed() * 31L);
		Integer cached;
		synchronized (SURFACE) {
			cached = SURFACE.get(key);
		}
		if (cached == null) {
			cached = Math.max(terrain.surface(x, z), terrain.generator().getSeaLevel() - 1);
			synchronized (SURFACE) {
				SURFACE.put(key, cached);
			}
		}
		return cached;
	}
}
