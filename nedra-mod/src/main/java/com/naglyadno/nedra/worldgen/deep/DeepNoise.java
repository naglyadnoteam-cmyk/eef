package com.naglyadno.nedra.worldgen.deep;

import java.util.SplittableRandom;

/**
 * Улучшенный шум Перлина (Ken Perlin, 2002) с перестановкой и смещением от зерна. Неизменяем после
 * создания, поэтому безопасен для параллельной генерации чанков.
 */
final class DeepNoise {

	private static final int[][] GRAD = {
			{1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0}, {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
			{0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}, {1, 1, 0}, {0, -1, 1}, {-1, 1, 0}, {0, -1, -1}};

	private final int[] perm = new int[512];
	private final double ox;
	private final double oy;
	private final double oz;

	DeepNoise(long seed) {
		SplittableRandom random = new SplittableRandom(seed);
		int[] p = new int[256];
		for (int i = 0; i < 256; i++) {
			p[i] = i;
		}
		for (int i = 255; i > 0; i--) {
			int j = random.nextInt(i + 1);
			int t = p[i];
			p[i] = p[j];
			p[j] = t;
		}
		for (int i = 0; i < 512; i++) {
			perm[i] = p[i & 255];
		}
		ox = random.nextDouble() * 256.0;
		oy = random.nextDouble() * 256.0;
		oz = random.nextDouble() * 256.0;
	}

	double sample(double x, double y, double z) {
		x += ox;
		y += oy;
		z += oz;
		int xi = (int) Math.floor(x);
		int yi = (int) Math.floor(y);
		int zi = (int) Math.floor(z);
		double xf = x - xi;
		double yf = y - yi;
		double zf = z - zi;
		xi &= 255;
		yi &= 255;
		zi &= 255;
		double u = fade(xf);
		double v = fade(yf);
		double w = fade(zf);
		int a = perm[xi] + yi;
		int aa = perm[a] + zi;
		int ab = perm[a + 1] + zi;
		int b = perm[xi + 1] + yi;
		int ba = perm[b] + zi;
		int bb = perm[b + 1] + zi;
		double x1 = lerp(u, grad(perm[aa], xf, yf, zf), grad(perm[ba], xf - 1, yf, zf));
		double x2 = lerp(u, grad(perm[ab], xf, yf - 1, zf), grad(perm[bb], xf - 1, yf - 1, zf));
		double y1 = lerp(v, x1, x2);
		double x3 = lerp(u, grad(perm[aa + 1], xf, yf, zf - 1), grad(perm[ba + 1], xf - 1, yf, zf - 1));
		double x4 = lerp(u, grad(perm[ab + 1], xf, yf - 1, zf - 1), grad(perm[bb + 1], xf - 1, yf - 1, zf - 1));
		double y2 = lerp(v, x3, x4);
		return lerp(w, y1, y2);
	}

	private static double fade(double t) {
		return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
	}

	private static double lerp(double t, double a, double b) {
		return a + t * (b - a);
	}

	private static double grad(int hash, double x, double y, double z) {
		int[] g = GRAD[hash & 15];
		return g[0] * x + g[1] * y + g[2] * z;
	}
}
