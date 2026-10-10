package com.naglyadno.nedra.worldgen.deep;

import com.naglyadno.nedra.NedraMod;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureSet;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.gen.chunk.placement.StructurePlacement;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Замёрзшие пещеры - редкие ледяные залы на случайной высоте под землёй (примерно так же редко, как
 * древние города). Мир поделён на ячейки CELL x CELL; в части ячеек стоит одна пещера.
 *
 * <p>Пещера - несколько слившихся куполов (лопастей) с общим полом. Вся форма - детерминированная
 * функция зерна и координат ({@link #sample}), поэтому каждый чанк строит ровно свою часть, а
 * соседние чанки сходятся без швов. Высота выбирается так, чтобы свод оставался глубоко под
 * поверхностью (по оценке высоты рельефа генератором), а пещера не задевала крепости, древние
 * города и испытательные камеры.</p>
 */
public final class FrozenCaverns {

	public static final RegistryKey<Biome> BIOME = RegistryKey.of(RegistryKeys.BIOME, Identifier.of(NedraMod.MOD_ID, "frozen_caverns"));

	public static final int CELL = 384;
	private static final double CHANCE = 0.42;
	/** Самый низкий пол: ниже начинается лава у дна мира. */
	private static final int LOWEST_FLOOR = -316;
	/** Насколько свод пещеры (с ледяной коркой) должен быть ниже поверхности. */
	private static final int SURFACE_MARGIN = 18;
	/** Над древними городами и испытательными камерами пещера не поднимается выше этого уровня. */
	private static final int BELOW_VANILLA_STRUCTURES = -84;

	/** Лопасть пещеры: смещение от центра, полуоси эллипса, поворот и высота купола. */
	public record Lobe(double ox, double oz, double rx, double rz, double cos, double sin, double height) {
	}

	/** Туннель: точки оси через каждый блок и радиус в каждой точке. */
	public record Tunnel(double[] xs, double[] ys, double[] zs, double[] radius) {
	}

	/** Ледопад: опорная точка у стены, направление наружу (ux, uz) и полуширина. */
	public record Fall(double ax, double az, double ux, double uz, double halfWidth) {
	}

	/** Лагерь: центр, уровень пола и направление на центр пещеры. */
	public record Camp(int x, int y, int z, int fx, int fz) {
	}

	public static final class Site {
		public final int cx;
		public final int cz;
		public final int floorY;
		public final long seed;
		public final Lobe[] lobes;
		public final double lakeBias;
		/** Горизонтальный радиус самой пещеры (без туннелей). */
		public final int reach;
		public final int maxHeight;
		Tunnel[] tunnels = new Tunnel[0];
		Fall[] falls = new Fall[0];
		Camp camp;

		Site(int cx, int cz, int floorY, long seed, Lobe[] lobes, double lakeBias, int reach, int maxHeight) {
			this.cx = cx;
			this.cz = cz;
			this.floorY = floorY;
			this.seed = seed;
			this.lobes = lobes;
			this.lakeBias = lakeBias;
			this.reach = reach;
			this.maxHeight = maxHeight;
		}

		public Tunnel[] tunnels() {
			return tunnels;
		}

		public Fall[] falls() {
			return falls;
		}

		public Camp camp() {
			return camp;
		}

		/** Радиус с туннелями - всё, что пещера может затронуть. */
		public int fullReach() {
			int r = reach;
			for (Tunnel tunnel : tunnels) {
				for (int i = 0; i < tunnel.xs().length; i++) {
					r = (int) Math.max(r, Math.hypot(tunnel.xs()[i] - cx, tunnel.zs()[i] - cz) + tunnel.radius()[i] + 3);
				}
			}
			return r;
		}

		public int topY() {
			return floorY + maxHeight + 8;
		}

		public int bottomY() {
			return floorY - 12;
		}
	}

	/** Данные колонны (x, z) пещеры. */
	public record ColumnInfo(double e, boolean cavity, boolean lake, boolean island, int top, int lakeFloor, int ceil) {
		/** Свободная высота над полом. */
		public int room() {
			return ceil - top;
		}
	}

	private record Key(long seed, int cellX, int cellZ) {
	}

	private static final Map<Key, Optional<Site>> SITES = new ConcurrentHashMap<>();
	private static final Map<Long, Noises> NOISES = new ConcurrentHashMap<>();

	/** Шумы формы пещер (отдельные от шумов недр, чтобы пещеры не повторяли их рисунок). */
	static final class Noises {
		final DeepNoise outline;
		final DeepNoise outlineFine;
		final DeepNoise floor;
		final DeepNoise lake;
		final DeepNoise island;
		final DeepNoise dome;
		final DeepNoise ripple;
		final DeepNoise material;

		Noises(long seed) {
			long s = seed * 0x5DEECE66DL + 0x7A11;
			outline = new DeepNoise(s + 1);
			outlineFine = new DeepNoise(s + 2);
			floor = new DeepNoise(s + 3);
			lake = new DeepNoise(s + 4);
			island = new DeepNoise(s + 5);
			dome = new DeepNoise(s + 6);
			ripple = new DeepNoise(s + 7);
			material = new DeepNoise(s + 8);
		}
	}

	private FrozenCaverns() {
	}

	static Noises noises(long seed) {
		if (NOISES.size() > 8) {
			NOISES.clear();
		}
		return NOISES.computeIfAbsent(seed, Noises::new);
	}

	/** Шум материалов отделки (-1..1) - общий для всех частей пещеры. */
	public static double materialNoise(long seed, double x, double y, double z) {
		return noises(seed).material.sample(x, y, z);
	}

	// ------------------------------------------------------------------ места

	public static Site siteInCell(ServerWorld world, int cellX, int cellZ) {
		long seed = world.getSeed();
		if (SITES.size() > 512) {
			SITES.clear();
		}
		Key key = new Key(seed, cellX, cellZ);
		Optional<Site> cached = SITES.get(key);
		if (cached != null) {
			return cached.orElse(null);
		}
		Site site = DeepTerrain.hash01(seed, cellX, 40, cellZ, 1201) > CHANCE ? null : create(world, seed, cellX, cellZ);
		SITES.putIfAbsent(key, Optional.ofNullable(site));
		return SITES.get(key).orElse(null);
	}

	private static double h(long seed, int cellX, int cellZ, int salt) {
		return DeepTerrain.hash01(seed, cellX, 41 + salt, cellZ, 1300 + salt);
	}

	private static Site create(ServerWorld world, long seed, int cellX, int cellZ) {
		// форма
		double rx = 40 + h(seed, cellX, cellZ, 1) * 18;
		double rz = rx * (0.72 + h(seed, cellX, cellZ, 2) * 0.28);
		double height = 26 + h(seed, cellX, cellZ, 3) * 12;
		double angle = h(seed, cellX, cellZ, 4) * Math.PI;
		List<Lobe> lobes = new ArrayList<>();
		lobes.add(new Lobe(0, 0, rx, rz, Math.cos(angle), Math.sin(angle), height));
		int extra = 2 + (h(seed, cellX, cellZ, 5) < 0.5 ? 1 : 0);
		double start = h(seed, cellX, cellZ, 6) * Math.PI * 2;
		for (int i = 0; i < extra; i++) {
			double a = start + i * (Math.PI * 2 / extra) + (h(seed, cellX, cellZ, 10 + i) - 0.5) * 1.1;
			double dist = rx * (0.55 + h(seed, cellX, cellZ, 20 + i) * 0.3);
			double lrx = rx * (0.42 + h(seed, cellX, cellZ, 30 + i) * 0.22);
			double lrz = lrx * (0.7 + h(seed, cellX, cellZ, 40 + i) * 0.3);
			double la = h(seed, cellX, cellZ, 50 + i) * Math.PI;
			double lh = height * (0.58 + h(seed, cellX, cellZ, 60 + i) * 0.27);
			lobes.add(new Lobe(Math.cos(a) * dist, Math.sin(a) * dist, lrx, lrz, Math.cos(la), Math.sin(la), lh));
		}
		int reach = 0;
		for (Lobe lobe : lobes) {
			reach = (int) Math.max(reach, Math.hypot(lobe.ox(), lobe.oz()) + Math.max(lobe.rx(), lobe.rz()) * 1.3 + 4);
		}
		int cx = cellX * CELL + reach + 40 + (int) (h(seed, cellX, cellZ, 7) * (CELL - 2 * reach - 80));
		int cz = cellZ * CELL + reach + 40 + (int) (h(seed, cellX, cellZ, 8) * (CELL - 2 * reach - 80));

		// не рядом с поселениями шахтёров и цитаделями (они в недрах, но проще не пересекаться совсем)
		Settlements.Site town = Settlements.nearest(seed, cx, cz, 1);
		if (town != null && town.distance(cx, cz) < town.radius() + reach + 70) {
			return null;
		}
		if (!Citadels.near(seed, cx - reach - 70, cz - reach - 70, cx + reach + 70, cz + reach + 70, 0).isEmpty()) {
			return null;
		}

		// высота: от дна недр до глубины под самой низкой точкой рельефа над пещерой
		int maxHeight = (int) Math.ceil(height);
		int surface = lowestSurface(world, cx, cz, reach + 70);
		int highestFloor = surface - SURFACE_MARGIN - maxHeight - 10;
		Structures structures = structures(world);
		if (structures.nearStronghold(cx, cz, reach + 140)) {
			return null;
		}
		if (structures.nearVanillaDeepStructure(seed, cx, cz, reach + 120)) {
			highestFloor = Math.min(highestFloor, BELOW_VANILLA_STRUCTURES - maxHeight - 8);
		}
		if (highestFloor < LOWEST_FLOOR + 6) {
			return null;
		}
		int floorY = LOWEST_FLOOR + (int) (h(seed, cellX, cellZ, 9) * (highestFloor - LOWEST_FLOOR));
		long siteSeed = (long) (h(seed, cellX, cellZ, 11) * Long.MAX_VALUE);
		double lakeBias = -0.08 + h(seed, cellX, cellZ, 12) * 0.3;
		Site site = new Site(cx, cz, floorY, siteSeed, lobes.toArray(new Lobe[0]), lakeBias, reach, maxHeight);
		site.tunnels = planTunnels(world, site, seed, cellX, cellZ);
		site.falls = planFalls(site, seed, cellX, cellZ);
		site.camp = planCamp(site, seed, cellX, cellZ);
		return site;
	}

	private static int lowestSurface(ServerWorld world, int x, int z, int radius) {
		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		NoiseConfig noise = world.getChunkManager().getNoiseConfig();
		int lowest = generator.getHeight(x, z, Heightmap.Type.OCEAN_FLOOR_WG, world, noise);
		for (int ring = 1; ring <= 2; ring++) {
			double r = radius * ring / 2.0;
			for (int i = 0; i < 8; i++) {
				double a = i * Math.PI / 4 + ring * 0.4;
				int sx = x + (int) Math.round(Math.cos(a) * r);
				int sz = z + (int) Math.round(Math.sin(a) * r);
				lowest = Math.min(lowest, generator.getHeight(sx, sz, Heightmap.Type.OCEAN_FLOOR_WG, world, noise));
			}
		}
		return lowest;
	}

	// ------------------------------------------------------------------ ванильные структуры

	private record Structures(StructurePlacementCalculator calculator, List<ChunkPos> strongholds,
			List<RandomSpreadStructurePlacement> deep) {

		boolean nearStronghold(int x, int z, int radius) {
			for (ChunkPos pos : strongholds) {
				if (Math.hypot(pos.getCenterX() - x, pos.getCenterZ() - z) < radius) {
					return true;
				}
			}
			return false;
		}

		/** Древние города и испытательные камеры: проверяем все точки-кандидаты их сетки рядом. */
		boolean nearVanillaDeepStructure(long seed, int x, int z, int radius) {
			long structureSeed = calculator.getStructureSeed();
			for (RandomSpreadStructurePlacement placement : deep) {
				int spacing = placement.getSpacing();
				int minRegionX = Math.floorDiv((x - radius) >> 4, spacing);
				int maxRegionX = Math.floorDiv((x + radius) >> 4, spacing);
				int minRegionZ = Math.floorDiv((z - radius) >> 4, spacing);
				int maxRegionZ = Math.floorDiv((z + radius) >> 4, spacing);
				for (int rx = minRegionX; rx <= maxRegionX; rx++) {
					for (int rz = minRegionZ; rz <= maxRegionZ; rz++) {
						ChunkPos start = placement.getStartChunk(structureSeed, rx * spacing, rz * spacing);
						if (Math.hypot(start.getCenterX() - x, start.getCenterZ() - z) < radius) {
							return true;
						}
					}
				}
			}
			return false;
		}
	}

	private static Structures structures(ServerWorld world) {
		StructurePlacementCalculator calculator = world.getChunkManager().getStructurePlacementCalculator();
		Registry<StructureSet> sets = world.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE_SET);
		List<ChunkPos> strongholds = new ArrayList<>();
		List<RandomSpreadStructurePlacement> deep = new ArrayList<>();
		for (String id : new String[]{"strongholds", "ancient_cities", "trial_chambers"}) {
			Optional<RegistryEntry.Reference<StructureSet>> entry = sets.getEntry(Identifier.ofVanilla(id));
			if (entry.isEmpty()) {
				continue;
			}
			StructurePlacement placement = entry.get().value().placement();
			if (placement instanceof ConcentricRingsStructurePlacement rings) {
				List<ChunkPos> positions = calculator.getPlacementPositions(rings);
				if (positions != null) {
					strongholds.addAll(positions);
				}
			} else if (placement instanceof RandomSpreadStructurePlacement spread) {
				deep.add(spread);
			}
		}
		return new Structures(calculator, strongholds, deep);
	}

	// ------------------------------------------------------------------ поиск

	public static List<Site> near(ServerWorld world, int minX, int minZ, int maxX, int maxZ) {
		List<Site> result = new ArrayList<>(1);
		int pad = 200;
		for (int cx = Math.floorDiv(minX - pad, CELL); cx <= Math.floorDiv(maxX + pad, CELL); cx++) {
			for (int cz = Math.floorDiv(minZ - pad, CELL); cz <= Math.floorDiv(maxZ + pad, CELL); cz++) {
				Site site = siteInCell(world, cx, cz);
				if (site == null) {
					continue;
				}
				int r = site.fullReach();
				if (site.cx + r >= minX && site.cx - r <= maxX && site.cz + r >= minZ && site.cz - r <= maxZ) {
					result.add(site);
				}
			}
		}
		return result;
	}

	public static Site nearest(ServerWorld world, int x, int z, int maxCells) {
		int cx0 = Math.floorDiv(x, CELL);
		int cz0 = Math.floorDiv(z, CELL);
		Site best = null;
		double bestDist = Double.MAX_VALUE;
		for (int ring = 0; ring <= maxCells; ring++) {
			for (int cx = cx0 - ring; cx <= cx0 + ring; cx++) {
				for (int cz = cz0 - ring; cz <= cz0 + ring; cz++) {
					if (Math.max(Math.abs(cx - cx0), Math.abs(cz - cz0)) != ring) {
						continue;
					}
					Site site = siteInCell(world, cx, cz);
					if (site != null) {
						double d = Math.hypot(site.cx - x, site.cz - z);
						if (d < bestDist) {
							bestDist = d;
							best = site;
						}
					}
				}
			}
			// ближайшая найденная ячейка заведомо ближе всех следующих колец
			if (best != null && bestDist < ring * CELL) {
				break;
			}
		}
		return best;
	}

	/** Точка обзора: на берегу озера, взгляд через центр пещеры. */
	public static BlockPos viewPoint(ServerWorld world, Site site) {
		long seed = world.getSeed();
		Lobe main = site.lobes[0];
		for (int k = 0; k < 16; k++) {
			double a = k * Math.PI / 8;
			for (double t = 0.45; t <= 0.8; t += 0.05) {
				int x = site.cx + (int) Math.round(Math.cos(a) * main.rx() * t);
				int z = site.cz + (int) Math.round(Math.sin(a) * main.rx() * t);
				ColumnInfo column = sample(seed, site, x, z);
				if (column.cavity() && !column.lake() && column.room() >= 8) {
					return new BlockPos(x, column.top() + 1, z);
				}
			}
		}
		return new BlockPos(site.cx, site.floorY + 1, site.cz);
	}

	/** Точка внутри пещеры (с ледяной коркой) - там биом замёрзших пещер. */
	public static boolean inBiome(long worldSeed, Site site, int x, int y, int z) {
		if (Math.abs(x - site.cx) > site.reach + 4 || Math.abs(z - site.cz) > site.reach + 4
				|| y < site.bottomY() || y > site.topY()) {
			return false;
		}
		ColumnInfo c = sample(worldSeed, site, x, z);
		return c.cavity() && y >= (c.lake() ? c.lakeFloor() : c.top()) - 3 && y <= c.ceil() + 3;
	}

	/** Стоит ли точка в биоме замёрзших пещер. */
	public static boolean isFrozenBiome(ServerWorld world, BlockPos pos) {
		return world.getBiome(pos).matchesKey(BIOME);
	}

	// ------------------------------------------------------------------ форма

	/** Нормированное расстояние до края лопасти (1 - край) с неровным контуром. */
	private static double lobeE(Noises n, Site site, Lobe lobe, double x, double z) {
		double dx = x - site.cx - lobe.ox();
		double dz = z - site.cz - lobe.oz();
		double u = (dx * lobe.cos() + dz * lobe.sin()) / lobe.rx();
		double v = (-dx * lobe.sin() + dz * lobe.cos()) / lobe.rz();
		double wobble = 1.0 + 0.17 * n.outline.sample(x / 30.0, site.floorY * 0.01, z / 30.0)
				+ 0.06 * n.outlineFine.sample(x / 9.0, 3.7, z / 9.0);
		return Math.sqrt(u * u + v * v) / wobble;
	}

	public static ColumnInfo sample(long worldSeed, Site site, int x, int z) {
		Noises n = noises(worldSeed);
		double e = Double.MAX_VALUE;
		double dome = 0.0;
		for (Lobe lobe : site.lobes) {
			double le = lobeE(n, site, lobe, x, z);
			e = Math.min(e, le);
			if (le < 1.0) {
				dome = Math.max(dome, lobe.height() * Math.sqrt(1.0 - le * le));
			}
		}
		int base = site.floorY;
		if (e >= 1.0) {
			return new ColumnInfo(e, false, false, false, base + 8, base, base + 8);
		}
		double bowl = 8.0 * DeepTerrain.smooth((e - 0.5) / 0.5);
		double und = 1.5 * n.floor.sample(x / 16.0, 0.5, z / 16.0);
		double lakeVal = (0.6 - e) * 2.4 + 0.55 * n.lake.sample(x / 24.0, 1.5, z / 24.0) + site.lakeBias;
		boolean lake = false;
		boolean island = false;
		int top;
		int lakeFloor = base;
		if (lakeVal > 0.0) {
			double isl = n.island.sample(x / 10.0, 2.5, z / 10.0);
			if (isl > 0.36 && lakeVal < 0.95) {
				island = true;
				top = base + 1 + Math.min(2, (int) ((isl - 0.36) * 12));
			} else {
				lake = true;
				top = base;
				lakeFloor = base - (int) Math.round(Math.min(7.0, 1.0 + lakeVal * 10.0));
			}
		} else {
			top = base + (int) Math.round(Math.max(0.0, und + 0.8) + bowl);
		}
		double roof = dome * (0.86 + 0.14 * n.dome.sample(x / 20.0, 4.5, z / 20.0))
				- 2.4 * Math.abs(n.ripple.sample(x / 7.0, 5.5, z / 7.0));
		int ceil = base + (int) Math.floor(roof);
		boolean cavity = ceil >= top + 2;
		return new ColumnInfo(e, cavity, lake && cavity, island && cavity, top, lakeFloor, ceil);
	}

	// ------------------------------------------------------------------ планировка

	/** Шаг от центра в направлении (cos, sin), пока нормированное расстояние не достигнет target. */
	private static double[] edgePoint(long worldSeed, Site site, double cos, double sin, double target) {
		Noises n = noises(worldSeed);
		double px = site.cx;
		double pz = site.cz;
		for (int step = 0; step < site.reach * 2; step++) {
			px = site.cx + cos * step;
			pz = site.cz + sin * step;
			double e = Double.MAX_VALUE;
			for (Lobe lobe : site.lobes) {
				e = Math.min(e, lobeE(n, site, lobe, px, pz));
			}
			if (e >= target) {
				break;
			}
		}
		return new double[]{px, pz};
	}

	private static Tunnel[] planTunnels(ServerWorld world, Site site, long seed, int cellX, int cellZ) {
		int count = 2 + (h(seed, cellX, cellZ, 70) < 0.45 ? 1 : 0);
		double start = h(seed, cellX, cellZ, 71) * Math.PI * 2;
		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		NoiseConfig noise = world.getChunkManager().getNoiseConfig();
		Tunnel[] tunnels = new Tunnel[count];
		for (int i = 0; i < count; i++) {
			double a = start + i * Math.PI * 2 / count + (h(seed, cellX, cellZ, 72 + i) - 0.5) * 0.8;
			double cos = Math.cos(a);
			double sin = Math.sin(a);
			double[] p0 = edgePoint(seed, site, cos, sin, 0.8);
			ColumnInfo c0 = sample(seed, site, (int) Math.floor(p0[0]), (int) Math.floor(p0[1]));
			double y0 = c0.top() + 2.0;
			int length = 46 + (int) (h(seed, cellX, cellZ, 76 + i) * 28);
			double slope = 0.1 + h(seed, cellX, cellZ, 80 + i) * 0.28;
			double endX = p0[0] + cos * length;
			double endZ = p0[1] + sin * length;
			int surface = generator.getHeight((int) endX, (int) endZ, Heightmap.Type.OCEAN_FLOOR_WG, world, noise);
			if (y0 + slope * length > surface - 12) {
				slope = Math.max(-0.2, (surface - 12 - y0) / length);
			}
			double phase = h(seed, cellX, cellZ, 84 + i) * Math.PI * 2;
			double[] xs = new double[length];
			double[] ys = new double[length];
			double[] zs = new double[length];
			double[] rs = new double[length];
			for (int t = 0; t < length; t++) {
				double wiggle = 5.0 * Math.sin(t / 15.0 + phase) * Math.min(1.0, t / 12.0);
				xs[t] = p0[0] + cos * t - sin * wiggle;
				zs[t] = p0[1] + sin * t + cos * wiggle;
				ys[t] = y0 + slope * t + 1.6 * Math.sin(t / 11.0 + phase * 2);
				rs[t] = 2.4 + 0.6 * Math.sin(t / 7.0 + phase) + (t < 6 ? (6 - t) * 0.35 : 0.0);
			}
			tunnels[i] = new Tunnel(xs, ys, zs, rs);
		}
		return tunnels;
	}

	private static Fall[] planFalls(Site site, long seed, int cellX, int cellZ) {
		int count = 2 + (int) (h(seed, cellX, cellZ, 90) * 3);
		double start = h(seed, cellX, cellZ, 91) * Math.PI * 2;
		Fall[] falls = new Fall[count];
		for (int i = 0; i < count; i++) {
			double a = start + i * Math.PI * 2 / count + (h(seed, cellX, cellZ, 92 + i) - 0.5) * 0.7;
			double cos = Math.cos(a);
			double sin = Math.sin(a);
			double[] p = edgePoint(seed, site, cos, sin, 0.74 + h(seed, cellX, cellZ, 97 + i) * 0.06);
			falls[i] = new Fall(p[0], p[1], cos, sin, 2.5 + h(seed, cellX, cellZ, 102 + i) * 2.0);
		}
		return falls;
	}

	private static Camp planCamp(Site site, long seed, int cellX, int cellZ) {
		double start = h(seed, cellX, cellZ, 110) * Math.PI * 2;
		Lobe main = site.lobes[0];
		for (int k = 0; k < 24; k++) {
			double a = start + k * Math.PI / 12;
			double t = 0.42 + (k % 3) * 0.1;
			int x = site.cx + (int) Math.round(Math.cos(a) * main.rx() * t);
			int z = site.cz + (int) Math.round(Math.sin(a) * main.rz() * t);
			if (campFits(seed, site, x, z)) {
				int y = sample(seed, site, x, z).top();
				// лагерь смотрит входом на центр пещеры (по ближайшей стороне света)
				double dirX = -Math.cos(a);
				double dirZ = -Math.sin(a);
				int fx = Math.abs(dirX) >= Math.abs(dirZ) ? (dirX < 0 ? -1 : 1) : 0;
				int fz = fx == 0 ? (dirZ < 0 ? -1 : 1) : 0;
				return new Camp(x, y, z, fx, fz);
			}
		}
		return null;
	}

	private static boolean campFits(long seed, Site site, int x, int z) {
		ColumnInfo center = sample(seed, site, x, z);
		if (!center.cavity() || center.lake() || center.island() || center.room() < 9) {
			return false;
		}
		for (int dx = -4; dx <= 4; dx += 4) {
			for (int dz = -4; dz <= 4; dz += 4) {
				ColumnInfo c = sample(seed, site, x + dx, z + dz);
				if (!c.cavity() || c.lake() || c.room() < 6 || Math.abs(c.top() - center.top()) > 2) {
					return false;
				}
			}
		}
		return true;
	}
}
