package com.naglyadno.nedra.worldgen.deep;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Форма недр (ниже Y -64): детерминированная функция от зерна мира и координат. Её используют и генерация
 * чанков, и команда /nedra locate, поэтому пещеры, реки и ярусы непрерывно тянутся через границы чанков.
 *
 * <ul>
 *     <li>Ярусы биомов: Эхо-пустоты (-80…-176), Магнитные пещеры (-176…-272), а ниже -272 - Кристальные
 *     глубины и Заросшие глубины, которые делят самый нижний ярус. Каждый биом занимает пятна в несколько
 *     сотен блоков; границы ярусов плавные и слегка «гуляют».</li>
 *     <li>Пещеры: обычные «сырные» полости + форма своего биома (плоские залы с колоннами, высокие
 *     шахты, огромные круглые камеры) + длинные туннели-«спагетти».</li>
 *     <li>Две сети подземных рек (около Y -116 и -228), озёра на дне кристальных камер и болота
 *     в Заросших глубинах.</li>
 * </ul>
 */
public final class DeepTerrain {

	public enum Layer { NONE, ECHO, MAGNETIC, CRYSTAL, JUNGLE, SCARLET }

	public static final int TOP = -64;
	public static final int FLOOR = -344;
	/** Уровень воды озёр в кристальных глубинах. */
	public static final int LAKE_LEVEL = -318;
	/** Уровень воды болот в Заросших глубинах. */
	public static final int SWAMP_LEVEL = -321;
	public static final int RIVER1_Y = -116;
	public static final int RIVER2_Y = -228;

	private static final Map<Long, DeepTerrain> CACHE = new ConcurrentHashMap<>();

	private final DeepNoise c1, c2, e1, m1, k1, ta, tb, pil, r1, r1y, r2, r2y, jit, regE, regM, regC, regJ, j1, trunks,
			riverMask, regS, s1, lushNoise;

	private DeepTerrain(long seed) {
		long s = seed * 0x9E3779B97F4A7C15L;
		c1 = new DeepNoise(s + 1);
		c2 = new DeepNoise(s + 2);
		e1 = new DeepNoise(s + 3);
		m1 = new DeepNoise(s + 4);
		k1 = new DeepNoise(s + 5);
		ta = new DeepNoise(s + 6);
		tb = new DeepNoise(s + 7);
		pil = new DeepNoise(s + 8);
		r1 = new DeepNoise(s + 9);
		r1y = new DeepNoise(s + 10);
		r2 = new DeepNoise(s + 11);
		r2y = new DeepNoise(s + 12);
		jit = new DeepNoise(s + 13);
		regE = new DeepNoise(s + 14);
		regM = new DeepNoise(s + 15);
		regC = new DeepNoise(s + 16);
		regJ = new DeepNoise(s + 17);
		j1 = new DeepNoise(s + 18);
		trunks = new DeepNoise(s + 19);
		riverMask = new DeepNoise(s + 20);
		regS = new DeepNoise(s + 21);
		s1 = new DeepNoise(s + 22);
		lushNoise = new DeepNoise(s + 23);
	}

	public static DeepTerrain of(long seed) {
		if (CACHE.size() > 8) {
			CACHE.clear();
		}
		return CACHE.computeIfAbsent(seed, DeepTerrain::new);
	}

	// ------------------------------------------------------------------ ярусы и биомы

	/** Данные колонны (x, z), которые не зависят от высоты: сдвиг границ ярусов и сила биомов. */
	public record Column(double jitter, double echo, double magnetic, double crystal, double jungle, double scarlet) {
	}

	public Column column(int x, int z) {
		return new Column(
				jit.sample(x / 48.0, 0.3, z / 48.0) * 30.0,
				smooth((regE.sample(x / 240.0, 0.5, z / 240.0) - 0.02) / 0.12),
				smooth((regM.sample(x / 260.0, 0.5, z / 260.0) + 0.03) / 0.12),
				smooth((regC.sample(x / 280.0, 0.5, z / 280.0) + 0.08) / 0.12),
				smooth((regJ.sample(x / 300.0, 0.5, z / 300.0) + 0.02) / 0.12),
				// Алые гроты - редкие небольшие пятна (десятки блоков) там, где шум особенно высок
				smooth((regS.sample(x / 120.0, 0.5, z / 120.0) - 0.40) / 0.08));
	}

	public double weight(Column column, Layer layer, int y) {
		double yy = y + column.jitter();
		return switch (layer) {
			case ECHO -> band(yy, -80, -176) * column.echo();
			case MAGNETIC -> band(yy, -176, -272) * column.magnetic();
			// нижний ярус делят два биома: где выражены Заросшие глубины, кристаллы уступают им место
			case CRYSTAL -> band(yy, -272, Integer.MIN_VALUE) * column.crystal() * (1.0 - column.jungle());
			case JUNGLE -> band(yy, -272, Integer.MIN_VALUE) * column.jungle();
			// около Y -200; границы по высоте не "гуляют", чтобы грот оставался компактным
			case SCARLET -> band(y, -168, -236) * column.scarlet();
			case NONE -> 0.0;
		};
	}

	/** Преобладающий биом в точке или NONE, если ни один биом здесь не выражен. */
	public Layer dominant(Column column, int y) {
		if (y >= TOP) {
			return Layer.NONE;
		}
		// алый грот вкраплён внутрь Магнитных пещер и важнее их
		if (weight(column, Layer.SCARLET, y) > 0.5) {
			return Layer.SCARLET;
		}
		Layer best = Layer.NONE;
		double bestWeight = 0.5;
		for (Layer layer : new Layer[]{Layer.ECHO, Layer.MAGNETIC, Layer.CRYSTAL, Layer.JUNGLE}) {
			double w = weight(column, layer, y);
			if (w > bestWeight) {
				bestWeight = w;
				best = layer;
			}
		}
		return best;
	}

	private static double band(double yy, int top, int bottom) {
		double up = smooth((top - yy) / 14.0 + 0.5);
		double down = bottom == Integer.MIN_VALUE ? 1.0 : smooth((yy - bottom) / 14.0 + 0.5);
		return up * down;
	}

	// ------------------------------------------------------------------ плотность пещер

	/** Плотность в узле сетки (положительная - пустота). Генерация интерполирует её между узлами 4x4x4. */
	public double cornerDensity(Column column, int x, int y, int z) {
		double fade = clamp((TOP - y) / 14.0) * clamp((y - FLOOR) / 14.0);
		if (fade <= 0.0) {
			return -0.4;
		}
		double detail = 0.35 * c2.sample(x / 26.0, y / 16.0, z / 26.0);
		double plain = 0.8 * c1.sample(x / 72.0, y / 36.0, z / 72.0) + detail - 0.30;
		double d = plain;
		double we = weight(column, Layer.ECHO, y);
		if (we > 0.001) {
			d += (0.8 * e1.sample(x / 110.0, y / 20.0, z / 110.0) + detail - 0.10 - plain) * we;
		}
		double wm = weight(column, Layer.MAGNETIC, y);
		if (wm > 0.001) {
			d += (0.8 * m1.sample(x / 46.0, y / 80.0, z / 46.0) + detail - 0.14 - plain) * wm;
		}
		double wc = weight(column, Layer.CRYSTAL, y);
		if (wc > 0.001) {
			d += (0.8 * k1.sample(x / 62.0, y / 44.0, z / 62.0) + detail - 0.06 - plain) * wc;
		}
		double wj = weight(column, Layer.JUNGLE, y);
		if (wj > 0.001) {
			// широкие низкие залы-болота
			d += (0.8 * j1.sample(x / 90.0, y / 30.0, z / 90.0) + detail - 0.02 - plain) * wj;
		}
		double ws = weight(column, Layer.SCARLET, y);
		if (ws > 0.001) {
			// округлые залы-жеоды
			d += (0.8 * s1.sample(x / 34.0, y / 26.0, z / 34.0) + detail + 0.02 - d) * ws;
		}
		double wl = lushWeight(x, y, z);
		if (wl > 0.001) {
			// пышные карманы чуть просторнее окружающих пещер
			d += 0.18 * wl;
		}
		double tunnel = (0.055 - Math.max(Math.abs(ta.sample(x / 48.0, y / 32.0, z / 48.0)),
				Math.abs(tb.sample(x / 48.0, y / 32.0, z / 48.0)))) * 4.0;
		d = Math.max(d, tunnel);
		return d * fade - (1.0 - fade) * 0.4;
	}

	/**
	 * Сила пышного кармана (как ванильные пышные пещеры: мох, азалии, светящиеся ягоды) в точке, 0..1.
	 * Карманы редкие, только на глубине около -140…-215.
	 */
	public double lushWeight(int x, int y, int z) {
		if (y > -130 || y < -222) {
			return 0.0;
		}
		double band = smooth((-130 - y) / 10.0) * smooth((y + 222) / 10.0);
		return band * smooth((lushNoise.sample(x / 70.0, y / 34.0, z / 70.0) - 0.40) / 0.12);
	}

	public boolean lush(int x, int y, int z) {
		return lushWeight(x, y, z) > 0.5;
	}

	/** Колонна внутри зала Эхо-пустот, где стоит каменная колонна. */
	public boolean pillar(int x, int z) {
		return pil.sample(x / 9.0, 0.7, z / 9.0) > 0.42;
	}

	/** Колонна Заросших глубин, где от пола до свода стоит ствол древнего дерева. */
	public boolean trunk(int x, int z) {
		return trunks.sample(x / 6.0, 0.3, z / 6.0) > 0.5;
	}

	/** Колонна-препятствие (каменная колонна или ствол), которая не даёт пещере пройти сквозь неё. */
	public boolean blocked(Column column, int x, int y, int z) {
		return (weight(column, Layer.ECHO, y) > 0.5 && pillar(x, z)) || (weight(column, Layer.JUNGLE, y) > 0.5 && trunk(x, z));
	}

	/** Профиль реки в колонне: сила 0..1 (0 - не река) и высота уровня воды. */
	public record River(double strength, int waterY) {
		public static final River NONE = new River(0, 0);

		public int depth() {
			return (int) Math.round(1.0 + 3.0 * strength);
		}

		/** Высота свода над водой: выше перепада водопада, чтобы вода с верхней ступени падала в туннель. */
		public int airHeight() {
			return (int) Math.round(3.0 + 7.0 * Math.sqrt(strength));
		}
	}

	/** Масштаб русла: чем больше, тем длиннее и плавнее петляет река. */
	private static final double RIVER_SCALE = 380.0;
	/** Половина ширины русла в единицах шума. */
	private static final double RIVER_WIDTH = 0.02;
	/** Высота ступени: на каждой границе ступеней река обрывается водопадом. */
	public static final int RIVER_STEP = 5;

	/**
	 * Подземная река в колонне. Русло - линия нуля шума, река есть не везде: маска оставляет длинные
	 * участки по несколько сотен блоков и обрывает их (туннель сужается и кончается). Уровень воды
	 * идёт ступенями по RIVER_STEP блоков - между ступенями водопады.
	 */
	public River river(int which, int x, int z) {
		DeepNoise path = which == 1 ? r1 : r2;
		double r = path.sample(x / RIVER_SCALE, 0.1, z / RIVER_SCALE);
		double f = 1.0 - Math.abs(r) / RIVER_WIDTH;
		if (f <= 0.0) {
			return River.NONE;
		}
		f *= smooth((riverMask.sample(x / 900.0, which * 7.3, z / 900.0) + 0.08) / 0.12);
		if (f <= 0.02) {
			return River.NONE;
		}
		int base = which == 1 ? RIVER1_Y : RIVER2_Y;
		DeepNoise level = which == 1 ? r1y : r2y;
		int offset = (int) Math.floor(level.sample(x / 400.0, 0.2, z / 400.0) * 28.0 / RIVER_STEP) * RIVER_STEP;
		return new River(f, base + offset);
	}

	/**
	 * Направление течения реки в точке (единичный вектор x, z) или null, если реки нет: вдоль русла
	 * в сторону понижения уровня - туда же, куда падают водопады.
	 */
	public double[] riverFlow(int which, double x, double z) {
		DeepNoise path = which == 1 ? r1 : r2;
		DeepNoise level = which == 1 ? r1y : r2y;
		double gx = path.sample((x + 1) / RIVER_SCALE, 0.1, z / RIVER_SCALE) - path.sample((x - 1) / RIVER_SCALE, 0.1, z / RIVER_SCALE);
		double gz = path.sample(x / RIVER_SCALE, 0.1, (z + 1) / RIVER_SCALE) - path.sample(x / RIVER_SCALE, 0.1, (z - 1) / RIVER_SCALE);
		double len = Math.hypot(gx, gz);
		if (len < 1.0e-9) {
			return null;
		}
		double tx = -gz / len;
		double tz = gx / len;
		double ahead = level.sample((x + tx * 8) / 400.0, 0.2, (z + tz * 8) / 400.0);
		double behind = level.sample((x - tx * 8) / 400.0, 0.2, (z - tz * 8) / 400.0);
		return ahead > behind ? new double[]{-tx, -tz} : new double[]{tx, tz};
	}

	/**
	 * Пустота ли в точке по форме недр (без учёта рек, поселений и вырезателей). Считает те же 8 узлов
	 * сетки, что и генерация, поэтому совпадает с ней блок в блок.
	 */
	public boolean isCaveAir(int x, int y, int z) {
		if (y >= TOP || y <= FLOOR) {
			return false;
		}
		int x0 = Math.floorDiv(x, 4) * 4;
		int y0 = Math.floorDiv(y, 4) * 4;
		int z0 = Math.floorDiv(z, 4) * 4;
		double[] c = new double[8];
		for (int i = 0; i < 8; i++) {
			int cx = x0 + ((i & 1) != 0 ? 4 : 0);
			int cy = y0 + ((i & 2) != 0 ? 4 : 0);
			int cz = z0 + ((i & 4) != 0 ? 4 : 0);
			c[i] = cornerDensity(column(cx, cz), cx, cy, cz);
		}
		double d = trilinear(c, (x - x0) / 4.0, (y - y0) / 4.0, (z - z0) / 4.0);
		if (d <= 0.0) {
			return false;
		}
		return !blocked(column(x, z), x, y, z);
	}

	static double trilinear(double[] c, double tx, double ty, double tz) {
		double x00 = c[0] + (c[1] - c[0]) * tx;
		double x10 = c[2] + (c[3] - c[2]) * tx;
		double x01 = c[4] + (c[5] - c[4]) * tx;
		double x11 = c[6] + (c[7] - c[6]) * tx;
		double y0 = x00 + (x10 - x00) * ty;
		double y1 = x01 + (x11 - x01) * ty;
		return y0 + (y1 - y0) * tz;
	}

	static double smooth(double t) {
		t = clamp(t);
		return t * t * (3.0 - 2.0 * t);
	}

	static double clamp(double t) {
		return t < 0.0 ? 0.0 : (t > 1.0 ? 1.0 : t);
	}

	/** Детерминированное псевдослучайное число 0..1 для позиции (украшения, планировка). */
	public static double hash01(long seed, int x, int y, int z, int salt) {
		long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L) ^ (salt * 0x27D4EB2F165667C5L);
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		h *= 0xC4CEB9FE1A85EC53L;
		h ^= h >>> 33;
		return (h >>> 11) * 0x1.0p-53;
	}
}
