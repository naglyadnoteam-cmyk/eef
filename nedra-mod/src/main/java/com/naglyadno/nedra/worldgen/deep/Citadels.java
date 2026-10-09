package com.naglyadno.nedra.worldgen.deep;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хрустальные цитадели - подземные замки-лабиринты на глубине -110…-255. Мир поделён на ячейки
 * CELL x CELL, примерно в трети ячеек стоит одна цитадель. Всё (положение, комнаты, проходы, типы
 * комнат) выводится из зерна, поэтому каждый чанк строит ровно свою часть замка.
 *
 * <p>Цитадель - сетка GRID x GRID комнат с шагом ROOM блоков (стена + 13 блоков внутри). Проходы
 * между комнатами - лабиринт (случайный обход в глубину) с несколькими лишними связями, чтобы были
 * петли. Четыре центральные комнаты объединены в высокий зал - Сердце цитадели.</p>
 */
public final class Citadels {

	public static final int CELL = 448;
	private static final double CHANCE = 0.34;
	public static final int ROOM = 14;
	public static final int GRID = 6;
	public static final int SIZE = ROOM * GRID;
	/** Высота комнаты: пол на floorY - 1, свод на floorY + HEIGHT. */
	public static final int HEIGHT = 8;
	/** На сколько выше остальных комнат свод Сердца. */
	public static final int HEART_EXTRA = 6;
	public static final int HEART_MIN = GRID / 2 - 1;
	public static final int HEART_MAX = GRID / 2;
	/** Длина входного туннеля от внешней стены. */
	public static final int TUNNEL = 26;

	public enum RoomType { HALL, VAULT, GUARD, LIBRARY, GARDEN, HEART, ENTRANCE }

	/** x0, z0 - северо-западный угол (координата внешней стены); entranceSide 0..3 = север, восток, юг, запад. */
	public record Site(int x0, int floorY, int z0, long seed, int entranceSide, int entranceIndex) {

		public int maxX() {
			return x0 + SIZE;
		}

		public int maxZ() {
			return z0 + SIZE;
		}

		public int centerX() {
			return x0 + SIZE / 2;
		}

		public int centerZ() {
			return z0 + SIZE / 2;
		}

		/** Внутренняя область комнаты (i, j): x от roomX(i) до roomX(i) + ROOM - 2 включительно. */
		public int roomX(int i) {
			return x0 + i * ROOM + 1;
		}

		public int roomZ(int j) {
			return z0 + j * ROOM + 1;
		}
	}

	/** Комнаты и проходы одной цитадели. east[i][j] - проход из (i, j) в (i + 1, j); south - в (i, j + 1). */
	public record Layout(RoomType[][] types, boolean[][] east, boolean[][] south) {
	}

	private static final Map<Long, Layout> LAYOUTS = new ConcurrentHashMap<>();

	private Citadels() {
	}

	public static Site siteInCell(long worldSeed, int cellX, int cellZ) {
		if (DeepTerrain.hash01(worldSeed, cellX, 10, cellZ, 901) > CHANCE) {
			return null;
		}
		int x0 = cellX * CELL + 80 + (int) (DeepTerrain.hash01(worldSeed, cellX, 11, cellZ, 902) * (CELL - 160 - SIZE));
		int z0 = cellZ * CELL + 80 + (int) (DeepTerrain.hash01(worldSeed, cellX, 12, cellZ, 903) * (CELL - 160 - SIZE));
		int floor = -255 + (int) (DeepTerrain.hash01(worldSeed, cellX, 13, cellZ, 904) * 145);
		long seed = (long) (DeepTerrain.hash01(worldSeed, cellX, 14, cellZ, 905) * Long.MAX_VALUE);
		int side = (int) (DeepTerrain.hash01(worldSeed, cellX, 15, cellZ, 906) * 4) & 3;
		int index = 1 + (int) (DeepTerrain.hash01(worldSeed, cellX, 16, cellZ, 907) * (GRID - 2));
		Site site = new Site(x0, floor, z0, seed, side, index);
		// не строим цитадель вплотную к поселению шахтёров
		Settlements.Site town = Settlements.nearest(worldSeed, site.centerX(), site.centerZ(), 1);
		if (town != null && town.distance(site.centerX(), site.centerZ()) < town.radius() + SIZE * 0.75 + 30) {
			return null;
		}
		return site;
	}

	/** Цитадели, чья область (с входным туннелем и запасом margin) задевает прямоугольник. */
	public static List<Site> near(long worldSeed, int minX, int minZ, int maxX, int maxZ, int margin) {
		List<Site> result = new ArrayList<>(1);
		int reach = margin + TUNNEL + 2;
		for (int cx = Math.floorDiv(minX - reach, CELL); cx <= Math.floorDiv(maxX + reach, CELL); cx++) {
			for (int cz = Math.floorDiv(minZ - reach, CELL); cz <= Math.floorDiv(maxZ + reach, CELL); cz++) {
				Site site = siteInCell(worldSeed, cx, cz);
				if (site != null && site.maxX() + reach >= minX && site.x0() - reach <= maxX
						&& site.maxZ() + reach >= minZ && site.z0() - reach <= maxZ) {
					result.add(site);
				}
			}
		}
		return result;
	}

	public static Site nearest(long worldSeed, int x, int z, int maxCells) {
		int cx0 = Math.floorDiv(x, CELL);
		int cz0 = Math.floorDiv(z, CELL);
		Site best = null;
		double bestDist = Double.MAX_VALUE;
		for (int cx = cx0 - maxCells; cx <= cx0 + maxCells; cx++) {
			for (int cz = cz0 - maxCells; cz <= cz0 + maxCells; cz++) {
				Site site = siteInCell(worldSeed, cx, cz);
				if (site != null) {
					double d = Math.hypot(site.centerX() - x, site.centerZ() - z);
					if (d < bestDist) {
						bestDist = d;
						best = site;
					}
				}
			}
		}
		return best;
	}

	public static boolean isHeart(int i, int j) {
		return i >= HEART_MIN && i <= HEART_MAX && j >= HEART_MIN && j <= HEART_MAX;
	}

	/** Комната у входа (на краю сетки со стороны входа). */
	public static int[] entranceRoom(Site site) {
		return switch (site.entranceSide()) {
			case 0 -> new int[]{site.entranceIndex(), 0};
			case 1 -> new int[]{GRID - 1, site.entranceIndex()};
			case 2 -> new int[]{site.entranceIndex(), GRID - 1};
			default -> new int[]{0, site.entranceIndex()};
		};
	}

	public static Layout layout(Site site) {
		if (LAYOUTS.size() > 64) {
			LAYOUTS.clear();
		}
		return LAYOUTS.computeIfAbsent(site.seed(), s -> generate(site));
	}

	private static Layout generate(Site site) {
		Random random = new Random(site.seed());
		boolean[][] east = new boolean[GRID][GRID];
		boolean[][] south = new boolean[GRID][GRID];
		// лабиринт: обход в глубину по всем комнатам; Сердце - одна "комната" из четырёх клеток
		boolean[][] visited = new boolean[GRID][GRID];
		Deque<int[]> stack = new ArrayDeque<>();
		int[] start = entranceRoom(site);
		stack.push(start);
		visited[start[0]][start[1]] = true;
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		while (!stack.isEmpty()) {
			int[] cur = stack.peek();
			List<int[]> options = new ArrayList<>();
			for (int[] d : dirs) {
				int ni = cur[0] + d[0];
				int nj = cur[1] + d[1];
				if (ni >= 0 && nj >= 0 && ni < GRID && nj < GRID && !visited[ni][nj]) {
					options.add(new int[]{ni, nj});
				}
			}
			if (options.isEmpty()) {
				stack.pop();
				continue;
			}
			int[] next = options.get(random.nextInt(options.size()));
			connect(east, south, cur[0], cur[1], next[0], next[1]);
			if (isHeart(next[0], next[1])) {
				// войдя в Сердце, считаем посещёнными все его клетки
				for (int i = HEART_MIN; i <= HEART_MAX; i++) {
					for (int j = HEART_MIN; j <= HEART_MAX; j++) {
						if (!visited[i][j]) {
							visited[i][j] = true;
							stack.push(new int[]{i, j});
						}
					}
				}
			} else {
				visited[next[0]][next[1]] = true;
				stack.push(next);
			}
		}
		// несколько лишних проходов - петли, чтобы лабиринт не был одним длинным коридором
		for (int i = 0; i < GRID; i++) {
			for (int j = 0; j < GRID; j++) {
				if (i + 1 < GRID && random.nextFloat() < 0.14F) {
					east[i][j] = true;
				}
				if (j + 1 < GRID && random.nextFloat() < 0.14F) {
					south[i][j] = true;
				}
			}
		}
		// стены внутри Сердца убираются целиком (это один зал), поэтому связи там не важны
		RoomType[][] types = new RoomType[GRID][GRID];
		List<int[]> free = new ArrayList<>();
		for (int i = 0; i < GRID; i++) {
			for (int j = 0; j < GRID; j++) {
				if (isHeart(i, j)) {
					types[i][j] = RoomType.HEART;
				} else if (i == start[0] && j == start[1]) {
					types[i][j] = RoomType.ENTRANCE;
				} else {
					types[i][j] = RoomType.HALL;
					free.add(new int[]{i, j});
				}
			}
		}
		java.util.Collections.shuffle(free, random);
		RoomType[] special = {RoomType.GUARD, RoomType.GUARD, RoomType.GUARD, RoomType.GUARD, RoomType.VAULT, RoomType.VAULT,
				RoomType.VAULT, RoomType.LIBRARY, RoomType.LIBRARY, RoomType.GARDEN, RoomType.GARDEN, RoomType.GUARD, RoomType.VAULT};
		for (int k = 0; k < special.length && k < free.size(); k++) {
			int[] cell = free.get(k);
			types[cell[0]][cell[1]] = special[k];
		}
		return new Layout(types, east, south);
	}

	private static void connect(boolean[][] east, boolean[][] south, int i1, int j1, int i2, int j2) {
		if (i1 == i2) {
			south[i1][Math.min(j1, j2)] = true;
		} else {
			east[Math.min(i1, i2)][j1] = true;
		}
	}
}
