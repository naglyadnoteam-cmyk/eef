package com.naglyadno.nedra.worldgen.deep;

import com.naglyadno.nedra.worldgen.deep.DeepTerrain.Column;
import com.naglyadno.nedra.worldgen.deep.DeepTerrain.Layer;
import net.minecraft.util.math.BlockPos;

/**
 * Поиск мест недр без генерации чанков: всё считается по той же детерминированной функции, что и генерация.
 * Используется командой /nedra locate и тестами.
 */
public final class DeepLocator {

	private DeepLocator() {
	}

	public static BlockPos settlement(long seed, int x, int z) {
		Settlements.Site site = Settlements.nearest(seed, x, z, 10);
		return site == null ? null : new BlockPos(site.x() + 3, site.floorY(), site.z() + 3);
	}

	/** Пещера внутри ярко выраженной области биома (ищет по спирали до ~1600 блоков). */
	public static BlockPos layer(long seed, Layer layer, int x, int z) {
		DeepTerrain terrain = DeepTerrain.of(seed);
		int centerY = switch (layer) {
			case ECHO -> -128;
			case MAGNETIC -> -224;
			case CRYSTAL -> -300;
			case NONE -> -100;
		};
		for (int ring = 0; ring <= 64; ring++) {
			for (int i = -ring; i <= ring; i++) {
				for (int j = -ring; j <= ring; j++) {
					if (Math.max(Math.abs(i), Math.abs(j)) != ring) {
						continue;
					}
					int cx = x + i * 24;
					int cz = z + j * 24;
					Column column = terrain.column(cx, cz);
					if (terrain.weight(column, layer, centerY) < 0.85) {
						continue;
					}
					BlockPos cave = caveNear(terrain, cx, centerY, cz);
					if (cave != null) {
						return cave;
					}
				}
			}
		}
		return null;
	}

	/** Место на берегу подземной реки (1 - верхняя сеть около Y -116, 2 - нижняя около Y -228). */
	public static BlockPos river(long seed, int which, int x, int z) {
		DeepTerrain terrain = DeepTerrain.of(seed);
		for (int ring = 0; ring <= 200; ring++) {
			for (int i = -ring; i <= ring; i++) {
				for (int j = -ring; j <= ring; j++) {
					if (Math.max(Math.abs(i), Math.abs(j)) != ring) {
						continue;
					}
					int cx = x + i * 8;
					int cz = z + j * 8;
					DeepTerrain.River river = terrain.river(which, cx, cz);
					if (river.strength() < 0.7) {
						continue;
					}
					Settlements.Site site = Settlements.nearest(seed, cx, cz, 1);
					if (site == null || site.distance(cx, cz) > site.radius() + 12) {
						return new BlockPos(cx, river.waterY(), cz);
					}
				}
			}
		}
		return null;
	}

	/** Точка с полом под ногами и двумя блоками воздуха в пределах ±12 блоков от (x, y, z). */
	public static BlockPos caveNear(DeepTerrain terrain, int x, int y, int z) {
		for (int r = 0; r <= 12; r += 2) {
			for (int dx = -r; dx <= r; dx += 2) {
				for (int dz = -r; dz <= r; dz += 2) {
					for (int dy = -16; dy <= 16; dy++) {
						int px = x + dx;
						int py = y + dy;
						int pz = z + dz;
						if (terrain.isCaveAir(px, py, pz) && terrain.isCaveAir(px, py + 1, pz) && !terrain.isCaveAir(px, py - 1, pz)) {
							return new BlockPos(px, py, pz);
						}
					}
				}
			}
		}
		return null;
	}
}
