package com.naglyadno.nedra.worldgen.feature;

/**
 * Ярусы глубин. Границы ярусов слегка «гуляют» по высоте, а внутри яруса биом появляется пятнами
 * в несколько сотен блоков: в каждом ярусе есть и области мода, и продолжение обычных пещер.
 *
 * <pre>
 *   Y  -80 … -176   Эхо-пустоты        (~40% областей)
 *   Y -176 … -272   Магнитные пещеры   (~45% областей)
 *   Y -272 … -352   Кристальные глубины (~55% областей)
 * </pre>
 */
public final class DepthBands {

	public enum Layer { NONE, ECHO, MAGNETIC, CRYSTAL }

	public static final int ECHO_TOP = -80;
	public static final int MAGNETIC_TOP = -176;
	public static final int CRYSTAL_TOP = -272;

	private DepthBands() {
	}

	public static Layer layerAt(long seed, int x, int y, int z) {
		double jitter = (noise(seed ^ 0x51A7E5L, x, z, 48.0) - 0.5) * 20.0;
		double yy = y + jitter;
		if (yy > ECHO_TOP) {
			return Layer.NONE;
		}
		if (yy > MAGNETIC_TOP) {
			return noise(seed + 101L, x, z, 176.0) > 0.53 ? Layer.ECHO : Layer.NONE;
		}
		if (yy > CRYSTAL_TOP) {
			return noise(seed + 202L, x, z, 200.0) > 0.51 ? Layer.MAGNETIC : Layer.NONE;
		}
		return noise(seed + 303L, x, z, 224.0) > 0.48 ? Layer.CRYSTAL : Layer.NONE;
	}

	/** Плавный 2D-шум 0..1 (две октавы значения на решётке), детерминированный от зерна мира. */
	static double noise(long seed, int x, int z, double scale) {
		double a = valueNoise(seed, x / scale, z / scale);
		double b = valueNoise(seed * 31L + 7L, x / (scale * 0.5), z / (scale * 0.5));
		return a * 0.7 + b * 0.3;
	}

	private static double valueNoise(long seed, double x, double z) {
		int x0 = (int) Math.floor(x);
		int z0 = (int) Math.floor(z);
		double tx = smooth(x - x0);
		double tz = smooth(z - z0);
		double v00 = lattice(seed, x0, z0);
		double v10 = lattice(seed, x0 + 1, z0);
		double v01 = lattice(seed, x0, z0 + 1);
		double v11 = lattice(seed, x0 + 1, z0 + 1);
		double top = v00 + (v10 - v00) * tx;
		double bottom = v01 + (v11 - v01) * tx;
		return top + (bottom - top) * tz;
	}

	private static double smooth(double t) {
		return t * t * (3.0 - 2.0 * t);
	}

	private static double lattice(long seed, int x, int z) {
		long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		h *= 0xC4CEB9FE1A85EC53L;
		h ^= h >>> 33;
		return (h >>> 11) * 0x1.0p-53;
	}
}
