package com.naglyadno.nedra.worldgen.deep;

import java.util.ArrayList;
import java.util.List;

/**
 * Заброшенные подземные поселения шахтёров в Эхо-пустотах. Мир поделён на ячейки 288x288 блоков; примерно
 * в половине ячеек есть одно поселение. Всё расположение (центр, высота, радиус, дома) выводится из зерна,
 * поэтому каждый чанк строит ровно свою часть, а поселение целиком собирается из десятков чанков.
 */
public final class Settlements {

	public static final int CELL = 288;
	private static final double CHANCE = 0.45;
	public static final int HOUSE_STEP = 12;

	/** floorY - высота пола зала (блок пола на floorY - 1). */
	public record Site(int x, int floorY, int z, int radius, long seed) {

		public double distance(int px, int pz) {
			return Math.hypot(px - x, pz - z);
		}

		/** Высота свода зала над полом в данной колонне (0 - вне зала). */
		public int roofHeight(int px, int pz) {
			double d = distance(px, pz) / radius;
			if (d >= 1.0) {
				return 0;
			}
			return (int) Math.round(6.0 + 13.0 * Math.sqrt(1.0 - d * d));
		}
	}

	private Settlements() {
	}

	public static Site siteInCell(long worldSeed, int cellX, int cellZ) {
		if (DeepTerrain.hash01(worldSeed, cellX, 0, cellZ, 701) > CHANCE) {
			return null;
		}
		int x = cellX * CELL + 64 + (int) (DeepTerrain.hash01(worldSeed, cellX, 1, cellZ, 702) * (CELL - 128));
		int z = cellZ * CELL + 64 + (int) (DeepTerrain.hash01(worldSeed, cellX, 2, cellZ, 703) * (CELL - 128));
		int floor = -138 + (int) (DeepTerrain.hash01(worldSeed, cellX, 3, cellZ, 704) * 40);
		int radius = 36 + (int) (DeepTerrain.hash01(worldSeed, cellX, 4, cellZ, 705) * 18);
		long seed = (long) (DeepTerrain.hash01(worldSeed, cellX, 5, cellZ, 706) * Long.MAX_VALUE);
		return new Site(x, floor, z, radius, seed);
	}

	/** Поселения, чей зал может задевать прямоугольник [minX..maxX] x [minZ..maxZ] (с запасом margin). */
	public static List<Site> near(long worldSeed, int minX, int minZ, int maxX, int maxZ, int margin) {
		List<Site> result = new ArrayList<>(1);
		int c0x = Math.floorDiv(minX - margin, CELL);
		int c1x = Math.floorDiv(maxX + margin, CELL);
		int c0z = Math.floorDiv(minZ - margin, CELL);
		int c1z = Math.floorDiv(maxZ + margin, CELL);
		for (int cx = c0x; cx <= c1x; cx++) {
			for (int cz = c0z; cz <= c1z; cz++) {
				Site site = siteInCell(worldSeed, cx, cz);
				if (site != null && site.x + site.radius + margin >= minX && site.x - site.radius - margin <= maxX
						&& site.z + site.radius + margin >= minZ && site.z - site.radius - margin <= maxZ) {
					result.add(site);
				}
			}
		}
		return result;
	}

	/** Ближайшее к точке поселение в пределах maxCells ячеек или null. */
	public static Site nearest(long worldSeed, int x, int z, int maxCells) {
		int cx0 = Math.floorDiv(x, CELL);
		int cz0 = Math.floorDiv(z, CELL);
		Site best = null;
		double bestDist = Double.MAX_VALUE;
		for (int cx = cx0 - maxCells; cx <= cx0 + maxCells; cx++) {
			for (int cz = cz0 - maxCells; cz <= cz0 + maxCells; cz++) {
				Site site = siteInCell(worldSeed, cx, cz);
				if (site != null && site.distance(x, z) < bestDist) {
					bestDist = site.distance(x, z);
					best = site;
				}
			}
		}
		return best;
	}

	// ------------------------------------------------------------------ планировка

	/** Дом на узле сетки (gx, gz) относительно центра; null - на этом месте дома нет. */
	public record House(int cx, int cz, int half, int doorDx, int doorDz, double variant) {
	}

	public static House house(Site site, int gx, int gz) {
		if (gx == 0 && gz == 0) {
			return null;
		}
		int hx = site.x + gx * HOUSE_STEP;
		int hz = site.z + gz * HOUSE_STEP;
		int half = DeepTerrain.hash01(site.seed, gx, 7, gz, 711) < 0.6 ? 3 : 2;
		if (site.distance(hx, hz) > site.radius - half - 4) {
			return null;
		}
		if (DeepTerrain.hash01(site.seed, gx, 8, gz, 712) > 0.82) {
			return null;
		}
		int ddx = 0;
		int ddz = 0;
		if (Math.abs(gx) >= Math.abs(gz)) {
			ddx = gx > 0 ? -1 : 1;
		} else {
			ddz = gz > 0 ? -1 : 1;
		}
		return new House(hx, hz, half, ddx, ddz, DeepTerrain.hash01(site.seed, gx, 9, gz, 713));
	}

	/** Улица: полоса между рядами домов. */
	public static boolean street(Site site, int x, int z) {
		int lx = Math.floorMod(x - site.x + HOUSE_STEP / 2, HOUSE_STEP);
		int lz = Math.floorMod(z - site.z + HOUSE_STEP / 2, HOUSE_STEP);
		return lx <= 1 || lx >= HOUSE_STEP - 1 || lz <= 1 || lz >= HOUSE_STEP - 1;
	}
}
