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
			case SCARLET -> -202;
			case CRYSTAL, JUNGLE -> -300;
			case NONE -> -100;
		};
		// Алые гроты маленькие - их ищем более частой сеткой
		int step = layer == Layer.SCARLET ? 8 : 24;
		int rings = layer == Layer.SCARLET ? 200 : 64;
		for (int ring = 0; ring <= rings; ring++) {
			for (int i = -ring; i <= ring; i++) {
				for (int j = -ring; j <= ring; j++) {
					if (Math.max(Math.abs(i), Math.abs(j)) != ring) {
						continue;
					}
					int cx = x + i * step;
					int cz = z + j * step;
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

	/** Точка обзора и направление взгляда (yaw). */
	public record View(BlockPos pos, float yaw) {
	}

	/** Ближайшая Хрустальная цитадель или null. */
	public static Citadels.Site citadel(long seed, int x, int z) {
		return Citadels.nearest(seed, x, z, 6);
	}

	/** Конец входного туннеля цитадели - снаружи, в пещерах. */
	public static BlockPos citadelEntrance(Citadels.Site site) {
		int[] room = Citadels.entranceRoom(site);
		int cx = site.roomX(room[0]) + 6;
		int cz = site.roomZ(room[1]) + 6;
		int out = Citadels.TUNNEL - 1;
		return switch (site.entranceSide()) {
			case 0 -> new BlockPos(cx, site.floorY(), site.z0() - out);
			case 1 -> new BlockPos(site.maxX() + out, site.floorY(), cz);
			case 2 -> new BlockPos(cx, site.floorY(), site.maxZ() + out);
			default -> new BlockPos(site.x0() - out, site.floorY(), cz);
		};
	}

	/** Пышный карман (мох, азалии, светящиеся ягоды) около -140…-215. */
	public static BlockPos lush(long seed, int x, int z) {
		DeepTerrain terrain = DeepTerrain.of(seed);
		for (int ring = 0; ring <= 120; ring++) {
			for (int i = -ring; i <= ring; i++) {
				for (int j = -ring; j <= ring; j++) {
					if (Math.max(Math.abs(i), Math.abs(j)) != ring) {
						continue;
					}
					int cx = x + i * 12;
					int cz = z + j * 12;
					for (int y = -150; y >= -205; y -= 11) {
						if (terrain.lushWeight(cx, y, cz) < 0.9) {
							continue;
						}
						BlockPos cave = caveNear(terrain, cx, y, cz);
						if (cave != null && terrain.lush(cave.getX(), cave.getY() - 1, cave.getZ())) {
							return cave;
						}
					}
				}
			}
		}
		return null;
	}

	/**
	 * Водопад подземной реки: точка в воде нижней ступени в нескольких блоках ниже по течению от
	 * перепада и взгляд вверх по течению - на падающую воду.
	 */
	public static View waterfall(long seed, int which, int x, int z) {
		DeepTerrain terrain = DeepTerrain.of(seed);
		for (int ring = 0; ring <= 400; ring++) {
			for (int i = -ring; i <= ring; i++) {
				for (int j = -ring; j <= ring; j++) {
					if (Math.max(Math.abs(i), Math.abs(j)) != ring) {
						continue;
					}
					int cx = x + i * 6;
					int cz = z + j * 6;
					DeepTerrain.River here = terrain.river(which, cx, cz);
					if (here.strength() < 0.75) {
						continue;
					}
					double[] flow = terrain.riverFlow(which, cx, cz);
					if (flow == null) {
						continue;
					}
					// вода падает, если чуть ниже по течению уровень на ступень ниже
					int ax = (int) Math.round(cx + flow[0] * 5);
					int az = (int) Math.round(cz + flow[1] * 5);
					DeepTerrain.River below = terrain.river(which, ax, az);
					if (below.strength() < 0.5 || below.waterY() >= here.waterY()) {
						continue;
					}
					Settlements.Site site = Settlements.nearest(seed, cx, cz, 1);
					if (site != null && site.distance(cx, cz) < site.radius() + 14) {
						continue;
					}
					int vx = (int) Math.round(cx + flow[0] * 12);
					int vz = (int) Math.round(cz + flow[1] * 12);
					DeepTerrain.River view = terrain.river(which, vx, vz);
					if (view.strength() < 0.4) {
						continue;
					}
					float yaw = (float) Math.toDegrees(Math.atan2(flow[0], -flow[1]));
					return new View(new BlockPos(vx, view.waterY(), vz), yaw);
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

	/**
	 * Самая просторная точка с полом под ногами и двумя блоками воздуха в пределах ±15 блоков от (x, y, z):
	 * сначала собираются кандидаты, затем выбирается тот, вокруг которого больше всего пустоты.
	 */
	public static BlockPos caveNear(DeepTerrain terrain, int x, int y, int z) {
		BlockPos best = null;
		int bestOpen = -1;
		int candidates = 0;
		for (int dx = -15; dx <= 15 && candidates < 40; dx += 3) {
			for (int dz = -15; dz <= 15 && candidates < 40; dz += 3) {
				for (int dy = -16; dy <= 16; dy++) {
					int px = x + dx;
					int py = y + dy;
					int pz = z + dz;
					if (terrain.isCaveAir(px, py, pz) && terrain.isCaveAir(px, py + 1, pz) && !terrain.isCaveAir(px, py - 1, pz)) {
						candidates++;
						int open = openness(terrain, px, py, pz);
						if (open > bestOpen) {
							bestOpen = open;
							best = new BlockPos(px, py, pz);
						}
						break;
					}
				}
			}
		}
		return best;
	}

	private static int openness(DeepTerrain terrain, int x, int y, int z) {
		int open = 0;
		for (int dx = -9; dx <= 9; dx += 3) {
			for (int dz = -9; dz <= 9; dz += 3) {
				for (int dy = 0; dy <= 9; dy += 3) {
					if (terrain.isCaveAir(x + dx, y + dy, z + dz)) {
						open++;
					}
				}
			}
		}
		return open;
	}

	/** Направление взгляда (yaw) вдоль самого длинного свободного пролёта - для красивого вида на пещеру. */
	public static float bestYaw(long seed, BlockPos pos) {
		DeepTerrain terrain = DeepTerrain.of(seed);
		float bestYaw = 0;
		int bestRun = -1;
		for (int i = 0; i < 8; i++) {
			double angle = Math.toRadians(i * 45.0);
			double sx = -Math.sin(angle);
			double sz = Math.cos(angle);
			int run = 0;
			for (int step = 1; step <= 40; step++) {
				int px = (int) Math.floor(pos.getX() + 0.5 + sx * step);
				int pz = (int) Math.floor(pos.getZ() + 0.5 + sz * step);
				if (!terrain.isCaveAir(px, pos.getY() + 1, pz)) {
					break;
				}
				run++;
			}
			if (run > bestRun) {
				bestRun = run;
				bestYaw = i * 45f;
			}
		}
		return bestYaw;
	}
}
