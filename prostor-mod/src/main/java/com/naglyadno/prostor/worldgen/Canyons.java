package com.naglyadno.prostor.worldgen;

import net.minecraft.util.math.noise.SimplexNoiseSampler;
import net.minecraft.util.math.random.Random;

/**
 * Русла речных каньонов: линии, где шум близок к нулю, - извилистая сеть ущелий через весь биом.
 * Профиль каньона зависит только от координат и сида, поэтому одинаков в любом чанке.
 */
public final class Canyons {

	/** Масштаб извилин (блоков на единицу шума). */
	private static final double SCALE = 240.0;
	/** Полуширина каньона в единицах шума: внутри - дно с рекой, к краю - стены. */
	public static final double WIDTH = 0.11;
	/** Доля полуширины, занятая ровным дном. */
	public static final double FLOOR = 0.32;

	private static long cachedSeed = Long.MIN_VALUE;
	private static SimplexNoiseSampler path;
	private static SimplexNoiseSampler wobble;

	private Canyons() {
	}

	private static synchronized void init(long seed) {
		if (cachedSeed != seed || path == null) {
			path = new SimplexNoiseSampler(Random.create(seed ^ 0x43414E594F4EL));
			wobble = new SimplexNoiseSampler(Random.create(seed ^ 0x574F42424C45L));
			cachedSeed = seed;
		}
	}

	/** Расстояние до оси каньона в его полуширинах: 0 - ось, 1 - край, больше - снаружи. */
	public static double u(long seed, double x, double z) {
		SimplexNoiseSampler p;
		SimplexNoiseSampler w;
		synchronized (Canyons.class) {
			init(seed);
			p = path;
			w = wobble;
		}
		// лёгкая рябь края, чтобы стены не были идеально гладкими
		double edge = 1.0 + 0.18 * w.sample(x / 37.0, z / 37.0);
		return Math.abs(p.sample(x / SCALE, z / SCALE)) / (WIDTH * edge);
	}
}
