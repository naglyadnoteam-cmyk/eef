package com.naglyadno.prostor.worldgen;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Геометрия озёр Озёрного края.
 *
 * <p>Мир разбит на клетки {@link #CELL}x{@link #CELL}; в клетке не больше одного озера, и оно целиком
 * лежит внутри клетки, поэтому озёра не сливаются. Форма, глубина и уровень воды озера вычисляются
 * только из сида мира и шумов рельефа (без чтения чанков), то есть одинаково для любого чанка в любом
 * порядке генерации. Каждый чанк потом меняет только свои собственные столбцы.
 */
public final class Lakes {

	public static final int CELL = 72;
	/** Ширина берега вокруг воды, который выравнивается под озеро (пологий подъём без обрывов). */
	public static final int BANK = 10;
	/** Подъём берега на блок расстояния от воды. */
	public static final double BANK_SLOPE = 0.7;
	private static final int MIN_RADIUS = 6;
	private static final int MAX_RADIUS = 13;
	private static final int RIM_SAMPLES = 28;

	/** Одно озеро. waterY - уровень поверхности воды (самый верхний блок воды). */
	public record Lake(int cellX, int cellZ, int centerX, int centerZ, int radius, int waterY, int maxDepth,
			float stretch, float angle, float a2, float p2, float a3, float p3,
			boolean hasIsland, int islandX, int islandZ, int islandRadius) {

		/** Нормированное расстояние: меньше 1 - вода, от 1 до 1 + BANK/r - берег. */
		public double q(double x, double z) {
			double dx = x - centerX;
			double dz = z - centerZ;
			double cos = Math.cos(angle);
			double sin = Math.sin(angle);
			double u = (dx * cos + dz * sin) / stretch;
			double v = -dx * sin + dz * cos;
			double dist = Math.sqrt(u * u + v * v);
			double theta = Math.atan2(v, u);
			double r = radius * (1.0 + a2 * Math.sin(2 * theta + p2) + a3 * Math.sin(3 * theta + p3));
			return dist / r;
		}

		/** Радиус-вектор формы в направлении theta (до учёта вытянутости). */
		public double radiusAt(double theta) {
			return radius * (1.0 + a2 * Math.sin(2 * theta + p2) + a3 * Math.sin(3 * theta + p3));
		}

		/** Точка на контуре воды, отнесённая наружу на extra блоков (в мировых координатах). */
		public double[] rimPoint(double theta, double extra) {
			double r = radiusAt(theta) + extra;
			double u = Math.cos(theta) * r * stretch;
			double v = Math.sin(theta) * r;
			double cos = Math.cos(angle);
			double sin = Math.sin(angle);
			return new double[]{centerX + u * cos - v * sin, centerZ + u * sin + v * cos};
		}

		/** Расстояние до острова в его радиусах (меньше 1 - суша острова). */
		public double islandQ(double x, double z) {
			if (!hasIsland) {
				return Double.MAX_VALUE;
			}
			double dx = x - islandX;
			double dz = z - islandZ;
			return Math.sqrt(dx * dx + dz * dz) / islandRadius;
		}

		/** Описывающий квадрат вместе с берегом. */
		public int reach() {
			return (int) Math.ceil(radius * 1.35 * stretch) + BANK + 1;
		}
	}

	/** Доступ к шумам мира, нужный для расчёта озёр (без чтения блоков). */
	public record Terrain(long seed, ChunkGenerator generator, NoiseConfig noise, HeightLimitView heights) {

		int surface(int x, int z) {
			return generator.getHeight(x, z, Heightmap.Type.OCEAN_FLOOR_WG, heights, noise) - 1;
		}

		int surfaceWithWater(int x, int z) {
			return generator.getHeight(x, z, Heightmap.Type.WORLD_SURFACE_WG, heights, noise) - 1;
		}

		boolean isLakeDistrict(int x, int y, int z) {
			BiomeSource source = generator.getBiomeSource();
			RegistryEntry<Biome> biome = source.getBiome(x >> 2, y >> 2, z >> 2, noise.getMultiNoiseSampler());
			return biome.matchesKey(ProstorBiomes.LAKE_DISTRICT);
		}
	}

	/** Кэш озёр: ключ - сид, конфигурация шумов и клетка. Рассчитанное озеро (или его отсутствие) не меняется. */
	private static final Map<CacheKey, Lake> CACHE = new LinkedHashMap<>(4096, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<CacheKey, Lake> eldest) {
			return size() > 8192;
		}
	};
	private static final Lake NONE = new Lake(0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, false, 0, 0, 0);

	/** Счётчики решений по клеткам (для замеров в тестах): см. {@link #REASONS}. */
	public static final String[] REASONS = {"empty", "not_in_biome", "rim_water", "slope", "edge_of_biome", "too_low", "hill", "lake"};
	public static final AtomicLongArray STATS = new AtomicLongArray(REASONS.length);

	private record CacheKey(long seed, int noiseId, int cellX, int cellZ) {
	}

	private Lakes() {
	}

	/** Все озёра, чьи берега задевают прямоугольник [minX..maxX] x [minZ..maxZ]. */
	public static List<Lake> near(Terrain terrain, int minX, int minZ, int maxX, int maxZ) {
		List<Lake> result = new ArrayList<>(2);
		for (int cx = Math.floorDiv(minX, CELL); cx <= Math.floorDiv(maxX, CELL); cx++) {
			for (int cz = Math.floorDiv(minZ, CELL); cz <= Math.floorDiv(maxZ, CELL); cz++) {
				Lake lake = lake(terrain, cx, cz);
				if (lake != null) {
					int reach = lake.reach();
					if (lake.centerX + reach >= minX && lake.centerX - reach <= maxX
							&& lake.centerZ + reach >= minZ && lake.centerZ - reach <= maxZ) {
						result.add(lake);
					}
				}
			}
		}
		return result;
	}

	/** Озеро клетки или null. */
	public static Lake lake(Terrain terrain, int cellX, int cellZ) {
		CacheKey key = new CacheKey(terrain.seed(), System.identityHashCode(terrain.noise()), cellX, cellZ);
		Lake cached;
		synchronized (CACHE) {
			cached = CACHE.get(key);
		}
		if (cached == null) {
			cached = compute(terrain, cellX, cellZ);
			synchronized (CACHE) {
				CACHE.put(key, cached);
			}
		}
		return cached == NONE ? null : cached;
	}

	private static Lake compute(Terrain terrain, int cellX, int cellZ) {
		Rand rnd = new Rand(terrain.seed(), cellX, cellZ);
		if (rnd.next() > 0.9) {
			STATS.incrementAndGet(0);
			return NONE;
		}
		// маленьких озёр больше, чем больших
		double size = rnd.next();
		int radius = MIN_RADIUS + (int) Math.round(Math.pow(size, 1.4) * (MAX_RADIUS - MIN_RADIUS));
		float stretch = 1.0f + (float) rnd.next() * 0.35f;
		float angle = (float) (rnd.next() * Math.PI);
		float a2 = 0.08f + (float) rnd.next() * 0.12f;
		float p2 = (float) (rnd.next() * Math.PI * 2);
		float a3 = 0.04f + (float) rnd.next() * 0.1f;
		float p3 = (float) (rnd.next() * Math.PI * 2);
		// озеро вместе с берегом целиком лежит в своей клетке, поэтому соседние озёра никогда не сливаются;
		// маленькие озёра можно сдвинуть по клетке сильнее, чем большие
		int reach = (int) Math.ceil(radius * 1.35 * stretch) + BANK + 1;
		int free = Math.max(0, CELL - 2 * reach);
		int cx = cellX * CELL + reach + (int) (rnd.next() * (free + 1));
		int cz = cellZ * CELL + reach + (int) (rnd.next() * (free + 1));
		if (!terrain.isLakeDistrict(cx, terrain.surface(cx, cz), cz)) {
			STATS.incrementAndGet(1);
			return NONE;
		}
		// если склон слишком крутой для большого озера, пробуем озеро поменьше
		int reason = 0;
		for (double shrink : new double[]{1.0, 0.75, 0.55}) {
			int r = Math.max(MIN_RADIUS - 1, (int) Math.round(radius * shrink));
			Lake shape = new Lake(cellX, cellZ, cx, cz, r, 0, 0, stretch, angle, a2, p2, a3, p3, false, 0, 0, 0);
			int[] result = new int[1];
			reason = check(terrain, shape, result);
			if (reason == 7) {
				STATS.incrementAndGet(7);
				return finish(rnd, shape, result[0]);
			}
		}
		STATS.incrementAndGet(reason);
		return NONE;
	}

	/** Проверяет место под озеро. Возвращает 7 и уровень воды в out[0], если озеро здесь хорошо смотрится. */
	private static int check(Terrain terrain, Lake shape, int[] out) {
		int radius = shape.radius();
		// весь берег и полоса за ним - в Озёрном крае, иначе озеро может врезаться в соседний биом или реку
		int minRim = Integer.MAX_VALUE;
		int maxRim = Integer.MIN_VALUE;
		for (int i = 0; i < RIM_SAMPLES; i++) {
			double theta = Math.PI * 2 * i / RIM_SAMPLES;
			double[] p = shape.rimPoint(theta, 1.0);
			int x = MathHelper.floor(p[0]);
			int z = MathHelper.floor(p[1]);
			int top = terrain.surface(x, z);
			if (terrain.surfaceWithWater(x, z) != top) {
				return 2; // на берегу уже есть вода: река, болото или океан
			}
			minRim = Math.min(minRim, top);
			maxRim = Math.max(maxRim, top);
			if (i % 4 == 0) {
				double[] far = shape.rimPoint(theta, BANK + 3.0);
				int fx = MathHelper.floor(far[0]);
				int fz = MathHelper.floor(far[1]);
				if (!terrain.isLakeDistrict(fx, terrain.surface(fx, fz), fz)) {
					return 4;
				}
			}
		}
		// слишком крутой склон: даже пологий берег не спасёт, озеро будет ямой со стеной с одной стороны
		if (maxRim - minRim > 6) {
			return 3;
		}
		int waterY = minRim;
		if (waterY < terrain.generator().getSeaLevel() + 2) {
			return 5;
		}
		// холм посреди озера: лучше без озера, чем с вырезанной в холме ямой
		for (int i = 0; i < 6; i++) {
			double[] p = shape.rimPoint(Math.PI * 2 * i / 6, -radius * 0.5);
			int top = terrain.surface(MathHelper.floor(p[0]), MathHelper.floor(p[1]));
			if (top > waterY + 7) {
				return 6;
			}
		}
		out[0] = waterY;
		return 7;
	}

	private static Lake finish(Rand rnd, Lake shape, int waterY) {
		int radius = shape.radius();
		int maxDepth = 3 + radius / 5 + (int) (rnd.next() * 2);
		boolean island = radius >= 9 && rnd.next() < 0.45;
		int islandX = 0;
		int islandZ = 0;
		int islandRadius = 0;
		if (island) {
			double theta = rnd.next() * Math.PI * 2;
			double[] p = shape.rimPoint(theta, -radius * (0.45 + rnd.next() * 0.2));
			islandX = MathHelper.floor(p[0]);
			islandZ = MathHelper.floor(p[1]);
			islandRadius = 2 + (int) (rnd.next() * 2.5);
		}
		return new Lake(shape.cellX(), shape.cellZ(), shape.centerX(), shape.centerZ(), radius, waterY, maxDepth,
				shape.stretch(), shape.angle(), shape.a2(), shape.p2(), shape.a3(), shape.p3(),
				island, islandX, islandZ, islandRadius);
	}

	/** Доля радиуса у кромки, где тянется мелководье глубиной в один блок (там растёт камыш). */
	public static final double SHELF = 0.8;

	/** Глубина воды над дном в точке с нормированным расстоянием q (0 - центр, 1 - кромка). */
	public static int depth(Lake lake, double q, double islandQ) {
		int d;
		if (q >= SHELF) {
			d = 1;
		} else {
			double k = q / SHELF;
			d = 1 + (int) Math.round((lake.maxDepth() - 1) * (1.0 - k * k));
		}
		if (islandQ < 2.2) {
			// у острова - отмель
			d = Math.min(d, 1 + (int) Math.floor((islandQ - 1.0) * 1.6));
		}
		return Math.max(1, d);
	}

	/** Расстояние от кромки воды в блоках (приблизительно). */
	public static double fromWater(Lake lake, double q) {
		return (q - 1.0) * lake.radius();
	}

	/** Положение на берегу: 0 у самой воды, 1 на внешнем краю выровненного берега. Больше 1 - уже не берег. */
	public static double bank(Lake lake, double q) {
		return fromWater(lake, q) / BANK;
	}

	/** Детерминированный генератор псевдослучайных чисел для клетки (не зависит от порядка генерации). */
	static final class Rand {
		private long state;

		Rand(long seed, int a, int b) {
			state = mix(seed ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL) ^ 0x5DEECE66DL);
		}

		double next() {
			state = mix(state + 0x9E3779B97F4A7C15L);
			return (state >>> 11) * 0x1.0p-53;
		}

		static long mix(long z) {
			z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
			z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
			return z ^ (z >>> 31);
		}
	}

	/** Хэш столбца в [0, 1): для мелких деталей (материал дна, растения), одинаковый при любой генерации. */
	public static double hash(long seed, int x, int z, int salt) {
		long h = Rand.mix(seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x632BE59BD9B4E019L));
		return (h >>> 11) * 0x1.0p-53;
	}

	public static boolean isLakeBiome(RegistryEntry<Biome> biome) {
		return biome.matchesKey(ProstorBiomes.LAKE_DISTRICT);
	}
}
