package com.naglyadno.prostor.worldgen;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Геометрия озёр и прудов.
 *
 * <p>Мир разбит на клетки размером {@link Style#cell()}; в клетке не больше одного водоёма, и он вместе
 * с берегом целиком лежит внутри клетки, поэтому водоёмы не сливаются. Форма, глубина и уровень воды
 * вычисляются только из сида мира и шумов рельефа (без чтения чанков), то есть одинаково для любого чанка
 * в любом порядке генерации. Каждый чанк потом меняет только свои собственные столбцы.
 */
public final class Lakes {

	private static final int RIM_SAMPLES = 28;

	/** Причины, по которым в клетке нет водоёма (для замеров в тестах). */
	public static final String[] REASONS = {"empty", "not_in_biome", "rim_water", "slope", "edge_of_biome", "too_low", "hill", "lake"};

	/**
	 * Стиль водоёмов биома.
	 *
	 * @param bank      ширина выравниваемого берега в блоках
	 * @param bankSlope подъём берега на блок расстояния от воды
	 * @param chance    доля клеток, где вообще пробуется водоём
	 * @param maxSlope  наибольший перепад высот по кромке
	 * @param minDepth  глубина самого маленького водоёма
	 * @param maxDepth  глубина самого большого водоёма
	 * @param shelf     доля радиуса, с которой начинается мелководье в один блок
	 */
	public record Style(String name, RegistryKey<Biome> biome, int salt, int cell, int minRadius, int maxRadius,
			int bank, double bankSlope, double chance, int maxSlope, int minDepth, int maxDepth,
			double islandChance, double shelf, AtomicLongArray stats) {

		Style(String name, RegistryKey<Biome> biome, int salt, int cell, int minRadius, int maxRadius, int bank,
				double bankSlope, double chance, int maxSlope, int minDepth, int maxDepth, double islandChance, double shelf) {
			this(name, biome, salt, cell, minRadius, maxRadius, bank, bankSlope, chance, maxSlope, minDepth, maxDepth,
					islandChance, shelf, new AtomicLongArray(REASONS.length));
		}

		/** Описывающий радиус водоёма с берегом (не больше половины клетки, иначе соседние водоёмы сольются). */
		int reach(int radius, float stretch) {
			return (int) Math.ceil(radius * 1.35 * stretch) + bank + 1;
		}
	}

	/** Озёра Озёрного края: от 6 до 13 блоков в радиусе, с островами и широким пологим берегом. */
	public static final Style LAKE_DISTRICT = new Style("lake_district", ProstorBiomes.LAKE_DISTRICT, 0,
			72, 6, 13, 10, 0.7, 0.9, 6, 3, 6, 0.45, 0.8);

	/** Болотные бочажины: частые, мелкие (1-2 блока), на ровном месте, с узким топким берегом. */
	public static final Style MARSH = new Style("marsh", ProstorBiomes.MARSH, 1,
			34, 3, 7, 3, 0.5, 0.85, 2, 1, 2, 0.0, 0.55);

	/** Один водоём. waterY - уровень поверхности воды (самый верхний блок воды). */
	public record Lake(Style style, int cellX, int cellZ, int centerX, int centerZ, int radius, int waterY, int maxDepth,
			float stretch, float angle, float a2, float p2, float a3, float p3,
			boolean hasIsland, int islandX, int islandZ, int islandRadius) {

		/** Нормированное расстояние: меньше 1 - вода, дальше - берег. */
		public double q(double x, double z) {
			double dx = x - centerX;
			double dz = z - centerZ;
			double cos = Math.cos(angle);
			double sin = Math.sin(angle);
			double u = (dx * cos + dz * sin) / stretch;
			double v = -dx * sin + dz * cos;
			double dist = Math.sqrt(u * u + v * v);
			double theta = Math.atan2(v, u);
			return dist / radiusAt(theta);
		}

		/** Радиус формы в направлении theta (до учёта вытянутости). */
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
			return style.reach(radius, stretch);
		}
	}

	/** Доступ к шумам мира, нужный для расчёта водоёмов (без чтения блоков). */
	public record Terrain(long seed, ChunkGenerator generator, NoiseConfig noise, HeightLimitView heights) {

		public int surface(int x, int z) {
			return generator.getHeight(x, z, Heightmap.Type.OCEAN_FLOOR_WG, heights, noise) - 1;
		}

		public int surfaceWithWater(int x, int z) {
			return generator.getHeight(x, z, Heightmap.Type.WORLD_SURFACE_WG, heights, noise) - 1;
		}

		public RegistryEntry<Biome> biome(int x, int y, int z) {
			return generator.getBiomeSource().getBiome(x >> 2, y >> 2, z >> 2, noise.getMultiNoiseSampler());
		}

		public boolean isBiome(RegistryKey<Biome> key, int x, int y, int z) {
			return biome(x, y, z).matchesKey(key);
		}
	}

	/** Кэш водоёмов: рассчитанный водоём (или его отсутствие) для клетки не меняется. */
	private static final Map<CacheKey, Lake> CACHE = new LinkedHashMap<>(4096, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<CacheKey, Lake> eldest) {
			return size() > 16384;
		}
	};
	private static final Lake NONE = new Lake(null, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, false, 0, 0, 0);

	private record CacheKey(long seed, int noiseId, int salt, int cellX, int cellZ) {
	}

	private Lakes() {
	}

	/** Все водоёмы стиля, чьи берега задевают прямоугольник [minX..maxX] x [minZ..maxZ]. */
	public static List<Lake> near(Style style, Terrain terrain, int minX, int minZ, int maxX, int maxZ) {
		List<Lake> result = new ArrayList<>(2);
		for (int cx = Math.floorDiv(minX, style.cell()); cx <= Math.floorDiv(maxX, style.cell()); cx++) {
			for (int cz = Math.floorDiv(minZ, style.cell()); cz <= Math.floorDiv(maxZ, style.cell()); cz++) {
				Lake lake = lake(style, terrain, cx, cz);
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

	/** Водоём клетки или null. */
	public static Lake lake(Style style, Terrain terrain, int cellX, int cellZ) {
		CacheKey key = new CacheKey(terrain.seed(), System.identityHashCode(terrain.noise()), style.salt(), cellX, cellZ);
		Lake cached;
		synchronized (CACHE) {
			cached = CACHE.get(key);
		}
		if (cached == null) {
			cached = compute(style, terrain, cellX, cellZ);
			synchronized (CACHE) {
				CACHE.put(key, cached);
			}
		}
		return cached == NONE ? null : cached;
	}

	private static Lake compute(Style style, Terrain terrain, int cellX, int cellZ) {
		Rand rnd = new Rand(terrain.seed() + style.salt() * 0x5851F42D4C957F2DL, cellX, cellZ);
		if (rnd.next() > style.chance()) {
			style.stats().incrementAndGet(0);
			return NONE;
		}
		// маленьких водоёмов больше, чем больших
		double size = rnd.next();
		int radius = style.minRadius() + (int) Math.round(Math.pow(size, 1.1) * (style.maxRadius() - style.minRadius()));
		float stretch = 1.0f + (float) rnd.next() * 0.35f;
		float angle = (float) (rnd.next() * Math.PI);
		float a2 = 0.08f + (float) rnd.next() * 0.12f;
		float p2 = (float) (rnd.next() * Math.PI * 2);
		float a3 = 0.04f + (float) rnd.next() * 0.1f;
		float p3 = (float) (rnd.next() * Math.PI * 2);
		// водоём вместе с берегом целиком лежит в своей клетке, поэтому соседние водоёмы никогда не сливаются;
		// маленькие можно сдвинуть по клетке сильнее, чем большие
		while (radius > 2 && style.reach(radius, stretch) * 2 > style.cell()) {
			radius--;
		}
		int reach = style.reach(radius, stretch);
		int free = Math.max(0, style.cell() - 2 * reach);
		int cx = cellX * style.cell() + reach + (int) (rnd.next() * (free + 1));
		int cz = cellZ * style.cell() + reach + (int) (rnd.next() * (free + 1));
		if (!terrain.isBiome(style.biome(), cx, terrain.surface(cx, cz), cz)) {
			style.stats().incrementAndGet(1);
			return NONE;
		}
		// если склон слишком крутой для большого водоёма, пробуем поменьше
		int reason = 0;
		for (double shrink : new double[]{1.0, 0.75, 0.55}) {
			int r = Math.max(Math.max(2, style.minRadius() - 1), (int) Math.round(radius * shrink));
			Lake shape = new Lake(style, cellX, cellZ, cx, cz, r, 0, 0, stretch, angle, a2, p2, a3, p3, false, 0, 0, 0);
			int[] result = new int[1];
			reason = check(style, terrain, shape, result);
			if (reason == 7) {
				style.stats().incrementAndGet(7);
				return finish(rnd, shape, result[0]);
			}
		}
		style.stats().incrementAndGet(reason);
		return NONE;
	}

	/** Проверяет место под водоём. Возвращает 7 и уровень воды в out[0], если водоём здесь хорошо смотрится. */
	private static int check(Style style, Terrain terrain, Lake shape, int[] out) {
		int radius = shape.radius();
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
			// вода и ближний берег - в своём биоме (дальний пологий склон может заходить к соседям)
			if (i % 2 == 0) {
				double[] far = shape.rimPoint(theta, Math.min(4.0, style.bank()));
				int fx = MathHelper.floor(far[0]);
				int fz = MathHelper.floor(far[1]);
				if (!terrain.isBiome(style.biome(), fx, terrain.surface(fx, fz), fz)) {
					return 4;
				}
			}
		}
		// слишком крутой склон: водоём был бы ямой со стеной с одной стороны
		if (maxRim - minRim > style.maxSlope()) {
			return 3;
		}
		int waterY = minRim;
		if (waterY < terrain.generator().getSeaLevel()) {
			return 5;
		}
		// холм посреди водоёма: лучше без него, чем с вырезанной в холме ямой
		for (int i = 0; i < 6; i++) {
			double[] p = shape.rimPoint(Math.PI * 2 * i / 6, -radius * 0.5);
			int top = terrain.surface(MathHelper.floor(p[0]), MathHelper.floor(p[1]));
			if (top > waterY + style.maxSlope() + 1) {
				return 6;
			}
		}
		out[0] = waterY;
		return 7;
	}

	private static Lake finish(Rand rnd, Lake shape, int waterY) {
		Style style = shape.style();
		int radius = shape.radius();
		double t = style.maxRadius() == style.minRadius() ? 1.0
				: (radius - style.minRadius()) / (double) (style.maxRadius() - style.minRadius());
		int maxDepth = style.minDepth() + (int) Math.round(Math.max(0, t) * (style.maxDepth() - style.minDepth()));
		if (style.maxDepth() > 2 && rnd.next() < 0.4) {
			maxDepth = Math.min(style.maxDepth(), maxDepth + 1);
		}
		boolean island = radius >= 9 && rnd.next() < style.islandChance();
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
		return new Lake(style, shape.cellX(), shape.cellZ(), shape.centerX(), shape.centerZ(), radius, waterY, maxDepth,
				shape.stretch(), shape.angle(), shape.a2(), shape.p2(), shape.a3(), shape.p3(),
				island, islandX, islandZ, islandRadius);
	}

	/** Глубина воды над дном в точке с нормированным расстоянием q (0 - центр, 1 - кромка). */
	public static int depth(Lake lake, double q, double islandQ) {
		double shelf = lake.style().shelf();
		int d;
		if (q >= shelf) {
			d = 1;
		} else {
			double k = q / shelf;
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
		return fromWater(lake, q) / lake.style().bank();
	}

	/** Детерминированный генератор псевдослучайных чисел для клетки (не зависит от порядка генерации). */
	public static final class Rand {
		private long state;

		public Rand(long seed, int a, int b) {
			state = mix(seed ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL) ^ 0x5DEECE66DL);
		}

		public double next() {
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
}
