package com.naglyadno.prostor.worldgen;

import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Места водопадов: в каждой клетке {@link #CELL}x{@link #CELL} биома ищется обрыв высотой от
 * {@link #MIN_DROP} блоков (по шумам рельефа, без чтения чанков). Наверху - ручей, впадающий в
 * кромку, внизу - озеро, куда падает вода.
 */
public final class Waterfalls {

	public static final int CELL = 96;
	public static final int MIN_DROP = 16;
	private static final int DIRECTIONS = 16;

	/**
	 * Водопад: кромка (ex, ez) на высоте воды topY, направление падения (dirX, dirZ), ширина width,
	 * озеро внизу с центром (px, pz), радиусом poolRadius и уровнем воды poolY.
	 */
	public record Site(int cellX, int cellZ, int ex, int ez, double dirX, double dirZ, int topY, int width,
			int px, int pz, int poolRadius, int poolY) {

		public int drop() {
			return topY - poolY;
		}

		/** Координаты вдоль ручья (s, против течения от кромки) и поперёк (k) для точки мира. */
		public double along(double x, double z) {
			return -((x - ex) * dirX + (z - ez) * dirZ);
		}

		public double across(double x, double z) {
			return (x - ex) * -dirZ + (z - ez) * dirX;
		}
	}

	private static final Map<Long, Site> CACHE = new LinkedHashMap<>(1024, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, Site> eldest) {
			return size() > 4096;
		}
	};
	private static final Site NONE = new Site(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

	private Waterfalls() {
	}

	public static List<Site> near(Lakes.Terrain terrain, int minX, int minZ, int maxX, int maxZ) {
		List<Site> result = new ArrayList<>(1);
		int margin = 24;
		for (int cx = Math.floorDiv(minX - margin, CELL); cx <= Math.floorDiv(maxX + margin, CELL); cx++) {
			for (int cz = Math.floorDiv(minZ - margin, CELL); cz <= Math.floorDiv(maxZ + margin, CELL); cz++) {
				Site site = site(terrain, cx, cz);
				if (site != null) {
					result.add(site);
				}
			}
		}
		return result;
	}

	public static Site site(Lakes.Terrain terrain, int cellX, int cellZ) {
		long key = (terrain.seed() * 31 + cellX) * 1_000_003L + cellZ;
		Site cached;
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

	private static Site compute(Lakes.Terrain terrain, int cellX, int cellZ) {
		Lakes.Rand rnd = new Lakes.Rand(terrain.seed() ^ 0x57415445524641L, cellX, cellZ);
		// несколько попыток найти обрыв в клетке
		for (int attempt = 0; attempt < 4; attempt++) {
			int x = cellX * CELL + 20 + (int) (rnd.next() * (CELL - 40));
			int z = cellZ * CELL + 20 + (int) (rnd.next() * (CELL - 40));
			Site site = tryAt(terrain, cellX, cellZ, x, z);
			if (site != null) {
				return site;
			}
		}
		return NONE;
	}

	private static Site tryAt(Lakes.Terrain terrain, int cellX, int cellZ, int x, int z) {
		int top = terrain.surface(x, z);
		if (!terrain.isBiome(ProstorBiomes.WATERFALL_CLIFFS, x, top, z) || terrain.surfaceWithWater(x, z) != top) {
			return null;
		}
		double bestDirX = 0;
		double bestDirZ = 0;
		int bestDrop = 0;
		for (int i = 0; i < DIRECTIONS; i++) {
			double a = Math.PI * 2 * i / DIRECTIONS;
			double dx = Math.cos(a);
			double dz = Math.sin(a);
			for (int d = 4; d <= 16; d += 2) {
				int h = terrain.surface(MathHelper.floor(x + dx * d), MathHelper.floor(z + dz * d));
				if (top - h > bestDrop) {
					bestDrop = top - h;
					bestDirX = dx;
					bestDirZ = dz;
				}
			}
		}
		if (bestDrop < MIN_DROP) {
			return null;
		}
		// наверху за кромкой нужна ровная площадка под ручей
		for (int s = 1; s <= 6; s++) {
			int h = terrain.surface(MathHelper.floor(x - bestDirX * s), MathHelper.floor(z - bestDirZ * s));
			if (Math.abs(h - top) > 2) {
				return null;
			}
		}
		// кромка - последняя точка не ниже верха на 2 блока
		int edge = 0;
		for (int s = 1; s <= 16; s++) {
			int h = terrain.surface(MathHelper.floor(x + bestDirX * s), MathHelper.floor(z + bestDirZ * s));
			if (h < top - 2) {
				break;
			}
			edge = s;
		}
		int ex = MathHelper.floor(x + bestDirX * edge);
		int ez = MathHelper.floor(z + bestDirZ * edge);
		// подножие: самая низкая точка в 1..20 блоках за кромкой
		int base = Integer.MAX_VALUE;
		int baseS = edge + 1;
		for (int s = edge + 1; s <= edge + 20; s++) {
			int bxp = MathHelper.floor(x + bestDirX * s);
			int bzp = MathHelper.floor(z + bestDirZ * s);
			int h = terrain.surface(bxp, bzp);
			if (h < base) {
				base = h;
				baseS = s;
			}
		}
		int drop = top - base;
		if (drop < MIN_DROP) {
			return null;
		}
		int px = MathHelper.floor(x + bestDirX * (baseS + 2));
		int pz = MathHelper.floor(z + bestDirZ * (baseS + 2));
		int poolY = terrain.surface(px, pz);
		if (terrain.surfaceWithWater(px, pz) != poolY || poolY < terrain.generator().getSeaLevel()) {
			return null;
		}
		// кромка и озеро - в своём биоме: тогда фича точно выполняется во всех чанках водопада
		if (!terrain.isBiome(ProstorBiomes.WATERFALL_CLIFFS, ex, top, ez)
				|| !terrain.isBiome(ProstorBiomes.WATERFALL_CLIFFS, px, poolY, pz)) {
			return null;
		}
		int width = drop >= 28 ? 4 : 3;
		return new Site(cellX, cellZ, ex, ez, bestDirX, bestDirZ, top - 1, width, px, pz, 4 + width / 2, poolY);
	}
}
