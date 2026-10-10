package com.naglyadno.nedra.worldgen.feature;

import com.mojang.serialization.Codec;
import com.naglyadno.nedra.NedraMod;
import com.naglyadno.nedra.block.ModBlocks;
import com.naglyadno.nedra.worldgen.deep.DeepTerrain;
import com.naglyadno.nedra.worldgen.deep.FrozenCaverns;
import com.naglyadno.nedra.worldgen.deep.FrozenCaverns.Camp;
import com.naglyadno.nedra.worldgen.deep.FrozenCaverns.ColumnInfo;
import com.naglyadno.nedra.worldgen.deep.FrozenCaverns.Fall;
import com.naglyadno.nedra.worldgen.deep.FrozenCaverns.Site;
import com.naglyadno.nedra.worldgen.deep.FrozenCaverns.Tunnel;
import net.minecraft.block.AmethystClusterBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.SnowBlock;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.loot.LootTable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

import java.util.List;

/**
 * Строит часть Замёрзшей пещеры, попадающую в чанк: ледяной купол, озеро подо льдом со снежными островками,
 * огромные сосульки и ледяные колонны, ледопады у стен, ледяные деревья, кристаллы инея, туннели наружу и
 * брошенный лагерь экспедиции. Работает последним шагом фич и пишет только в свой чанк; форма пещеры -
 * детерминированная функция {@link FrozenCaverns}, поэтому соседние чанки сходятся без швов.
 *
 * <p>Пещера запечатана: вода и лава в пределах нескольких блоков от полости замораживаются в плотный лёд,
 * поэтому ни подземные реки, ни лава не заливают её, а озеро не утекает в соседние пещеры. Сухие
 * пустоты рядом остаются - это естественные проходы в пещеру.</p>
 */
public class FrozenCavernFeature extends Feature<DefaultFeatureConfig> {

	private static final RegistryKey<LootTable> CAMP_LOOT = RegistryKey.of(RegistryKeys.LOOT_TABLE,
			Identifier.of(NedraMod.MOD_ID, "chests/frozen_camp"));

	public FrozenCavernFeature(Codec<DefaultFeatureConfig> codec) {
		super(codec);
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
		StructureWorldAccess world = context.getWorld();
		Chunk chunk = world.getChunk(context.getOrigin());
		int bx = chunk.getPos().getStartX();
		int bz = chunk.getPos().getStartZ();
		List<Site> sites = FrozenCaverns.near(world.toServerWorld(), bx, bz, bx + 15, bz + 15);
		for (Site site : sites) {
			new Builder(world, chunk, site, bx, bz).build();
		}
		return !sites.isEmpty();
	}

	private static final class Builder {

		private static final int B = 4;
		private static final int N = 16 + 2 * B;

		private static final BlockState AIR = Blocks.CAVE_AIR.getDefaultState();
		private static final BlockState WATER = Blocks.WATER.getDefaultState();
		private static final BlockState ICE = Blocks.ICE.getDefaultState();
		private static final BlockState PACKED = Blocks.PACKED_ICE.getDefaultState();
		private static final BlockState BLUE = Blocks.BLUE_ICE.getDefaultState();
		private static final BlockState SNOW_BLOCK = Blocks.SNOW_BLOCK.getDefaultState();
		private static final BlockState POWDER = Blocks.POWDER_SNOW.getDefaultState();
		private static final BlockState LEAVES = ModBlocks.FROST_LEAVES.getDefaultState();

		private final StructureWorldAccess world;
		private final Chunk chunk;
		private final Site site;
		private final long worldSeed;
		private final int bx;
		private final int bz;
		private final ColumnInfo[] cols = new ColumnInfo[N * N];

		Builder(StructureWorldAccess world, Chunk chunk, Site site, int bx, int bz) {
			this.world = world;
			this.chunk = chunk;
			this.site = site;
			this.worldSeed = world.getSeed();
			this.bx = bx;
			this.bz = bz;
		}

		// -------------------------------------------------------------- примитивы

		private boolean inside(int x, int z) {
			return x >= bx && x < bx + 16 && z >= bz && z < bz + 16;
		}

		private boolean validY(int y) {
			return y > chunk.getBottomY() + 4 && y < chunk.getBottomY() + chunk.getHeight() - 1;
		}

		private BlockState get(int x, int y, int z) {
			if (!inside(x, z) || !validY(y)) {
				return Blocks.STONE.getDefaultState();
			}
			return chunk.getSection(chunk.getSectionIndex(y)).getBlockState(x & 15, y & 15, z & 15);
		}

		private void set(int x, int y, int z, BlockState state) {
			if (inside(x, z) && validY(y)) {
				chunk.getSection(chunk.getSectionIndex(y)).setBlockState(x & 15, y & 15, z & 15, state);
			}
		}

		private void setIfAir(int x, int y, int z, BlockState state) {
			if (get(x, y, z).isAir()) {
				set(x, y, z, state);
			}
		}

		private static boolean isFluid(BlockState state) {
			return !state.getFluidState().isEmpty();
		}

		private double hash(int x, int y, int z, int salt) {
			return DeepTerrain.hash01(site.seed, x, y, z, salt);
		}

		private double noise(double x, double y, double z) {
			return FrozenCaverns.materialNoise(worldSeed, x, y, z);
		}

		/** Колонна из кеша (в пределах чанка с запасом B) или посчитанная заново. */
		private ColumnInfo col(int x, int z) {
			int ix = x - bx + B;
			int iz = z - bz + B;
			if (ix >= 0 && iz >= 0 && ix < N && iz < N) {
				ColumnInfo c = cols[ix * N + iz];
				if (c == null) {
					c = FrozenCaverns.sample(worldSeed, site, x, z);
					cols[ix * N + iz] = c;
				}
				return c;
			}
			return FrozenCaverns.sample(worldSeed, site, x, z);
		}

		private boolean inCavity(int x, int y, int z) {
			ColumnInfo c = col(x, z);
			return c.cavity() && y > c.top() && y <= c.ceil();
		}

		/** Вода озера и лёд над ней - их не трогают ни туннели, ни украшения. */
		private boolean inLake(int x, int y, int z) {
			ColumnInfo c = col(x, z);
			return c.lake() && y <= c.top() && y > c.lakeFloor();
		}

		private BlockState coat(int x, int y, int z) {
			double m = noise(x / 9.0, y / 9.0, z / 9.0);
			if (m > 0.42) {
				return BLUE;
			}
			return m < -0.45 ? SNOW_BLOCK : PACKED;
		}

		private BlockState ground(int x, int y, int z, int depth, boolean island) {
			if (island) {
				return depth < 2 ? SNOW_BLOCK : PACKED;
			}
			double m = noise(x / 11.0, y * 0.1 + 50.0, z / 11.0);
			if (depth == 0) {
				if (m > 0.5) {
					return PACKED;
				}
				return m < -0.6 ? BLUE : SNOW_BLOCK;
			}
			return depth < 3 && m < 0.3 ? SNOW_BLOCK : PACKED;
		}

		private boolean powder(int x, int z, ColumnInfo c) {
			return !c.lake() && !c.island() && c.e() < 0.85 && noise(x / 9.0, 77.0, z / 9.0) > 0.6;
		}

		private boolean nearCamp(int x, int z, int radius) {
			Camp camp = site.camp();
			return camp != null && Math.abs(x - camp.x()) <= radius && Math.abs(z - camp.z()) <= radius;
		}

		// -------------------------------------------------------------- сборка

		void build() {
			int r = site.fullReach() + 2;
			if (bx + 15 < site.cx - r || bx > site.cx + r || bz + 15 < site.cz - r || bz > site.cz + r) {
				return;
			}
			if (bx + 15 >= site.cx - site.reach && bx <= site.cx + site.reach
					&& bz + 15 >= site.cz - site.reach && bz <= site.cz + site.reach) {
				shell();
			}
			tunnels();
			icicles();
			spikes();
			falls();
			trees();
			camp();
			details();
		}

		/** Полость, ледяная корка свода и стен, пол и озеро. */
		private void shell() {
			for (int lx = 0; lx < 16; lx++) {
				for (int lz = 0; lz < 16; lz++) {
					int x = bx + lx;
					int z = bz + lz;
					ColumnInfo c = col(x, z);
					int maxCeilN = Integer.MIN_VALUE;
					int minTopN = Integer.MAX_VALUE;
					int minLakeFloorN = Integer.MAX_VALUE;
					int maxLakeTopN = Integer.MIN_VALUE;
					for (int dx = -2; dx <= 2; dx++) {
						for (int dz = -2; dz <= 2; dz++) {
							ColumnInfo n = col(x + dx, z + dz);
							if (!n.cavity()) {
								continue;
							}
							maxCeilN = Math.max(maxCeilN, n.ceil());
							minTopN = Math.min(minTopN, n.lake() ? n.lakeFloor() : n.top());
							if (n.lake()) {
								minLakeFloorN = Math.min(minLakeFloorN, n.lakeFloor());
								maxLakeTopN = Math.max(maxLakeTopN, n.top());
							}
						}
					}
					if (maxCeilN == Integer.MIN_VALUE) {
						continue;
					}
					if (c.cavity()) {
						cavityColumn(x, z, c, maxCeilN, minLakeFloorN);
					} else {
						rimColumn(x, z, minTopN, maxCeilN, maxLakeTopN);
					}
				}
			}
		}

		private void cavityColumn(int x, int z, ColumnInfo c, int maxCeilN, int minLakeFloorN) {
			// свод: корка по всей высоте, где соседние колонны выше (там стена видна сбоку), выше - заморозка жидкостей
			int plugTop = Math.max(c.ceil(), maxCeilN) + 3;
			for (int y = c.ceil() + 1; y <= plugTop; y++) {
				BlockState state = get(x, y, z);
				if (y <= Math.max(c.ceil() + 1, maxCeilN)) {
					if (!state.isAir()) {
						set(x, y, z, coat(x, y, z));
					}
				} else if (isFluid(state)) {
					set(x, y, z, PACKED);
				}
			}
			for (int y = c.top() + 1; y <= c.ceil(); y++) {
				set(x, y, z, AIR);
			}
			int bottom;
			if (c.lake()) {
				set(x, c.top(), z, ICE);
				for (int y = c.lakeFloor() + 1; y < c.top(); y++) {
					set(x, y, z, WATER);
				}
				double m = noise(x / 7.0, 13.0, z / 7.0);
				set(x, c.lakeFloor(), z, m > 0.25 ? Blocks.GRAVEL.getDefaultState()
						: (m < -0.35 ? Blocks.CLAY.getDefaultState() : PACKED));
				for (int y = c.lakeFloor() - 3; y < c.lakeFloor(); y++) {
					set(x, y, z, PACKED);
				}
				bottom = c.lakeFloor() - 3;
			} else {
				int groundBottom = c.top() - 3;
				if (minLakeFloorN != Integer.MAX_VALUE) {
					groundBottom = Math.min(groundBottom, minLakeFloorN - 2);
				}
				boolean powder = powder(x, z, c);
				for (int y = groundBottom; y <= c.top(); y++) {
					BlockState state = ground(x, y, z, c.top() - y, c.island());
					set(x, y, z, y == c.top() && powder ? POWDER : state);
				}
				bottom = groundBottom;
			}
			for (int y = bottom - 3; y < bottom; y++) {
				if (isFluid(get(x, y, z))) {
					set(x, y, z, PACKED);
				}
			}
		}

		/** Колонна у края полости: обледеневшая стена; сухие пустоты остаются проходами, жидкости замерзают. */
		private void rimColumn(int x, int z, int minTopN, int maxCeilN, int maxLakeTopN) {
			for (int y = minTopN - 3; y <= maxCeilN + 3; y++) {
				BlockState state = get(x, y, z);
				if (state.isAir()) {
					// у озера пустота ниже уровня льда стала бы дырой, куда утечёт вода
					if (y <= maxLakeTopN) {
						set(x, y, z, PACKED);
					}
				} else if (y <= maxCeilN) {
					set(x, y, z, y <= minTopN + 3 ? ground(x, y, z, 1, false) : coat(x, y, z));
				} else if (isFluid(state)) {
					set(x, y, z, PACKED);
				}
			}
		}

		// -------------------------------------------------------------- туннели наружу

		private void tunnels() {
			int index = 0;
			for (Tunnel tunnel : site.tunnels()) {
				index++;
				int len = tunnel.xs().length;
				boolean touches = false;
				for (int i = 0; i < len && !touches; i++) {
					double r = tunnel.radius()[i] + 3;
					touches = tunnel.xs()[i] + r >= bx && tunnel.xs()[i] - r <= bx + 15
							&& tunnel.zs()[i] + r >= bz && tunnel.zs()[i] - r <= bz + 15;
				}
				if (!touches) {
					continue;
				}
				// сначала корка и заморозка жидкостей вокруг, потом сама выемка - иначе корка заделала бы туннель
				for (int pass = 0; pass < 2; pass++) {
					for (int i = 0; i < len; i++) {
						tunnelPoint(tunnel, i, pass, len, index);
					}
				}
			}
		}

		private void tunnelPoint(Tunnel tunnel, int i, int pass, int len, int index) {
			double px = tunnel.xs()[i];
			double py = tunnel.ys()[i];
			double pz = tunnel.zs()[i];
			double r = tunnel.radius()[i];
			int reach = (int) Math.ceil(r + 2);
			if (px + reach < bx || px - reach > bx + 15 || pz + reach < bz || pz - reach > bz + 15) {
				return;
			}
			double frost = 1.0 - i / (len * 0.55);
			for (int x = (int) Math.floor(px - reach); x <= (int) Math.ceil(px + reach); x++) {
				for (int z = (int) Math.floor(pz - reach); z <= (int) Math.ceil(pz + reach); z++) {
					if (!inside(x, z)) {
						continue;
					}
					for (int y = (int) Math.floor(py - reach); y <= (int) Math.ceil(py + reach); y++) {
						double dx = x + 0.5 - px;
						double dy = (y + 0.5 - py) / 0.8;
						double dz = z + 0.5 - pz;
						double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
						// озеро и пол внутри пещеры туннель не трогает: ямка у воды стала бы течью
						ColumnInfo c = col(x, z);
						if (inLake(x, y, z) || (c.cavity() && y <= c.top())) {
							continue;
						}
						if (pass == 1) {
							if (d <= r) {
								set(x, y, z, AIR);
							}
						} else if (d > r && d <= r + 1.8 && !inCavity(x, y, z)) {
							BlockState state = get(x, y, z);
							if (isFluid(state)) {
								set(x, y, z, PACKED);
							} else if (!state.isAir() && d <= r + 1.0 && hash(x, y, z, 40 + index) < frost) {
								set(x, y, z, coat(x, y, z));
							}
						}
					}
				}
			}
		}

		// -------------------------------------------------------------- сосульки и колонны

		private void icicles() {
			int g = 7;
			for (int gx = Math.floorDiv(bx - 6 - site.cx, g); gx <= Math.floorDiv(bx + 21 - site.cx, g); gx++) {
				for (int gz = Math.floorDiv(bz - 6 - site.cz, g); gz <= Math.floorDiv(bz + 21 - site.cz, g); gz++) {
					if (hash(gx, 1, gz, 101) > 0.55) {
						continue;
					}
					int ax = site.cx + gx * g + 1 + (int) (hash(gx, 2, gz, 102) * (g - 2));
					int az = site.cz + gz * g + 1 + (int) (hash(gx, 3, gz, 103) * (g - 2));
					ColumnInfo a = col(ax, az);
					if (!a.cavity() || a.room() < 7 || nearCamp(ax, az, 6)) {
						continue;
					}
					double r = hash(gx, 4, gz, 104);
					boolean pillar = r > 0.93 && a.room() <= 30 && !a.lake();
					double length = pillar ? a.room() : a.room() * (0.16 + 0.52 * r * r);
					double radius = Math.max(1.0, Math.min(pillar ? 3.2 : 4.2, 0.9 + length / 8.0));
					int span = (int) Math.ceil(radius) + 3;
					for (int x = ax - span; x <= ax + span; x++) {
						for (int z = az - span; z <= az + span; z++) {
							if (!inside(x, z)) {
								continue;
							}
							ColumnInfo c = col(x, z);
							if (!c.cavity()) {
								continue;
							}
							double d = Math.hypot(x - ax, z - az);
							if (pillar) {
								for (int y = c.top() + 1; y <= c.ceil(); y++) {
									double flare = Math.max(0.0, 2.2 * (1.0 - (y - c.top() - 1) / 4.0))
											+ Math.max(0.0, 2.2 * (1.0 - (c.ceil() - y) / 4.0));
									double rr = radius * 0.75 + flare;
									if (d <= rr) {
										setIfAir(x, y, z, d < rr * 0.4 ? BLUE : PACKED);
									}
								}
							} else if (d < radius) {
								double hang = length * Math.pow(1.0 - d / radius, 1.5);
								int bottom = (int) Math.round(a.ceil() - hang);
								for (int y = Math.max(bottom, c.top() + 2); y <= c.ceil(); y++) {
									setIfAir(x, y, z, d < radius * 0.35 ? BLUE : PACKED);
								}
							}
						}
					}
					if (!pillar && length >= 3 && inside(ax, az)) {
						int tip = (int) Math.round(a.ceil() - length) - 1;
						if (tip > a.top() + 1) {
							setIfAir(ax, tip, az, ModBlocks.ICICLE.getDefaultState());
						}
					}
				}
			}
		}

		/** Ледяные шипы, растущие из пола (только на суше). */
		private void spikes() {
			int g = 11;
			for (int gx = Math.floorDiv(bx - 4 - site.cx, g); gx <= Math.floorDiv(bx + 19 - site.cx, g); gx++) {
				for (int gz = Math.floorDiv(bz - 4 - site.cz, g); gz <= Math.floorDiv(bz + 19 - site.cz, g); gz++) {
					if (hash(gx, 1, gz, 201) > 0.4) {
						continue;
					}
					int ax = site.cx + gx * g + 1 + (int) (hash(gx, 2, gz, 202) * (g - 2));
					int az = site.cz + gz * g + 1 + (int) (hash(gx, 3, gz, 203) * (g - 2));
					ColumnInfo a = col(ax, az);
					if (!a.cavity() || a.lake() || a.room() < 8 || powder(ax, az, a) || nearCamp(ax, az, 7)) {
						continue;
					}
					double height = a.room() * (0.15 + 0.35 * hash(gx, 4, gz, 204));
					double radius = Math.max(1.0, Math.min(2.6, 0.8 + height / 7.0));
					int span = (int) Math.ceil(radius) + 1;
					for (int x = ax - span; x <= ax + span; x++) {
						for (int z = az - span; z <= az + span; z++) {
							if (!inside(x, z)) {
								continue;
							}
							ColumnInfo c = col(x, z);
							double d = Math.hypot(x - ax, z - az);
							if (!c.cavity() || c.lake() || d >= radius) {
								continue;
							}
							int top = a.top() + (int) Math.round(height * Math.pow(1.0 - d / radius, 1.3));
							for (int y = c.top() + 1; y <= Math.min(top, c.ceil() - 1); y++) {
								setIfAir(x, y, z, d < radius * 0.4 ? BLUE : PACKED);
							}
						}
					}
				}
			}
		}

		// -------------------------------------------------------------- ледопады

		private void falls() {
			int index = 0;
			for (Fall fall : site.falls()) {
				index++;
				if (fall.ax() + 16 < bx || fall.ax() - 16 > bx + 15 || fall.az() + 16 < bz || fall.az() - 16 > bz + 15) {
					continue;
				}
				double tx = -fall.uz();
				double tz = fall.ux();
				int foot = col((int) Math.floor(fall.ax()), (int) Math.floor(fall.az())).top();
				for (int lx = 0; lx < 16; lx++) {
					for (int lz = 0; lz < 16; lz++) {
						int x = bx + lx;
						int z = bz + lz;
						double along = (x + 0.5 - fall.ax()) * tx + (z + 0.5 - fall.az()) * tz;
						double out = (x + 0.5 - fall.ax()) * fall.ux() + (z + 0.5 - fall.az()) * fall.uz();
						if (Math.abs(along) > fall.halfWidth() + 3.5 || out < -7 || out > 14) {
							continue;
						}
						ColumnInfo c = col(x, z);
						if (!c.cavity()) {
							continue;
						}
						for (int y = c.top() + 1; y <= c.ceil(); y++) {
							double rel = y - foot;
							// лицевая поверхность волнится, у подножия - застывшая наледь
							double front = -0.6 + 0.6 * Math.sin(y * 0.45 + along * 0.9);
							double flare = rel < 5 ? 4.5 * Math.pow((5.0 - Math.max(0.0, rel)) / 5.0, 2) : 0.0;
							double width = fall.halfWidth() + (rel < 3 ? 2.0 * (3.0 - Math.max(0.0, rel)) / 3.0 : 0.0);
							if (out < front - flare || Math.abs(along) > width) {
								continue;
							}
							int stripe = (int) Math.floor(along * 0.8 + 0.4 * Math.sin(y * 0.3));
							boolean edge = Math.abs(along) > width - 1.0;
							setIfAir(x, y, z, !edge && hash(stripe, index, 0, 301) < 0.5 ? BLUE : PACKED);
						}
					}
				}
			}
		}

		// -------------------------------------------------------------- ледяные деревья

		private void trees() {
			int g = 12;
			for (int gx = Math.floorDiv(bx - 6 - site.cx, g); gx <= Math.floorDiv(bx + 21 - site.cx, g); gx++) {
				for (int gz = Math.floorDiv(bz - 6 - site.cz, g); gz <= Math.floorDiv(bz + 21 - site.cz, g); gz++) {
					if (hash(gx, 1, gz, 401) > 0.5) {
						continue;
					}
					int ax = site.cx + gx * g + 2 + (int) (hash(gx, 2, gz, 402) * (g - 4));
					int az = site.cz + gz * g + 2 + (int) (hash(gx, 3, gz, 403) * (g - 4));
					ColumnInfo a = col(ax, az);
					if (!a.cavity() || a.lake() || a.room() < 11 || powder(ax, az, a) || nearCamp(ax, az, 8)) {
						continue;
					}
					tree(ax, az, a, gx, gz);
				}
			}
		}

		private void tree(int ax, int az, ColumnInfo a, int gx, int gz) {
			double rh = 2.4 + hash(gx, 5, gz, 405) * 1.3;
			double rv = 1.8 + hash(gx, 6, gz, 406) * 0.6;
			int trunk = 4 + (int) (hash(gx, 4, gz, 404) * 4);
			int room = a.ceil() - a.top();
			trunk = Math.min(trunk, room - (int) Math.ceil(rv) - 3);
			if (trunk < 3) {
				return;
			}
			int base = a.top();
			int cy = base + trunk + 1;
			// ствол с корнями
			for (int y = base + 1; y <= base + trunk; y++) {
				set(ax, y, az, PACKED);
			}
			int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
			for (int k = 0; k < 4; k++) {
				int[] d = dirs[k];
				if (hash(gx, 10 + k, gz, 407) < 0.6) {
					setIfAir(ax + d[0], base + 1, az + d[1], PACKED);
				}
			}
			// ветви из синего льда
			for (int k = 0; k < 4; k++) {
				int[] d = dirs[(int) (hash(gx, 20 + k, gz, 408) * dirs.length)];
				int y0 = base + trunk - 1 - (k % 2);
				for (int i = 1; i <= 2; i++) {
					setIfAir(ax + d[0] * i, y0 + (i - 1), az + d[1] * i, BLUE);
				}
			}
			// крона из ледяной листвы с прорехами, снегом сверху и свисающими прядями
			int sh = (int) Math.ceil(rh);
			int sv = (int) Math.ceil(rv);
			for (int dx = -sh; dx <= sh; dx++) {
				for (int dz = -sh; dz <= sh; dz++) {
					int x = ax + dx;
					int z = az + dz;
					if (!inside(x, z)) {
						continue;
					}
					double flat = (dx * dx + dz * dz) / (rh * rh);
					if (flat > 1.0) {
						continue;
					}
					int topLeaf = Integer.MIN_VALUE;
					int lowLeaf = Integer.MAX_VALUE;
					for (int dy = -sv; dy <= sv; dy++) {
						double q = flat + dy * dy / (rv * rv);
						int y = cy + dy;
						if (q > 1.0 || (q > 0.65 && hash(x, y, z, 409) < 0.35)) {
							continue;
						}
						if (get(x, y, z).isAir()) {
							set(x, y, z, LEAVES);
							topLeaf = Math.max(topLeaf, y);
							lowLeaf = Math.min(lowLeaf, y);
						}
					}
					if (topLeaf != Integer.MIN_VALUE && hash(x, topLeaf, z, 410) < 0.6) {
						setIfAir(x, topLeaf + 1, z, Blocks.SNOW.getDefaultState());
					}
					if (lowLeaf != Integer.MAX_VALUE && flat > 0.5) {
						double strand = hash(x, lowLeaf, z, 411);
						if (strand < 0.3) {
							int length = strand < 0.12 ? 2 : 1;
							for (int i = 1; i <= length; i++) {
								setIfAir(x, lowLeaf - i, z, LEAVES);
							}
						} else if (strand > 0.93) {
							// «плоды» - кристаллы инея под кроной
							setIfAir(x, lowLeaf - 1, z, crystal(Direction.DOWN, false));
						}
					}
				}
			}
		}

		private static BlockState crystal(Direction facing, boolean waterlogged) {
			return ModBlocks.FROST_CRYSTAL.getDefaultState().with(AmethystClusterBlock.FACING, facing)
					.with(AmethystClusterBlock.WATERLOGGED, waterlogged);
		}

		// -------------------------------------------------------------- лагерь экспедиции

		private void camp() {
			Camp camp = site.camp();
			if (camp == null || camp.x() + 6 < bx || camp.x() - 6 > bx + 15 || camp.z() + 6 < bz || camp.z() - 6 > bz + 15) {
				return;
			}
			int y = camp.y();
			for (int a = -4; a <= 4; a++) {
				for (int b = -4; b <= 4; b++) {
					int x = cx(camp, a, b);
					int z = cz(camp, a, b);
					if (!inside(x, z) || Math.max(Math.abs(a), Math.abs(b)) > 3) {
						continue;
					}
					for (int yy = y + 1; yy <= y + 5; yy++) {
						set(x, yy, z, AIR);
					}
					set(x, y, z, SNOW_BLOCK);
					for (int yy = y - 2; yy < y; yy++) {
						BlockState below = get(x, yy, z);
						if (below.isAir() || isFluid(below)) {
							set(x, yy, z, SNOW_BLOCK);
						}
					}
				}
			}
			BlockState wool = Blocks.WHITE_WOOL.getDefaultState();
			BlockState layer2 = Blocks.SNOW.getDefaultState().with(SnowBlock.LAYERS, 2);
			for (int a = -3; a <= -1; a++) {
				for (int b = -1; b <= 1; b++) {
					put(camp, a, y, b, Blocks.SPRUCE_PLANKS.getDefaultState());
				}
				put(camp, a, y + 1, -1, wool);
				put(camp, a, y + 1, 1, wool);
				put(camp, a, y + 2, 0, wool);
				put(camp, a, y + 2, -1, layer2);
				put(camp, a, y + 2, 1, layer2);
				put(camp, a, y + 3, 0, Blocks.SNOW.getDefaultState());
			}
			put(camp, -3, y + 1, 0, wool);
			Direction facing = facing(camp);
			int chestX = cx(camp, -2, 0);
			int chestZ = cz(camp, -2, 0);
			container(chestX, y + 1, chestZ, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, facing), 1);
			int barrelX = cx(camp, 0, -2);
			int barrelZ = cz(camp, 0, -2);
			container(barrelX, y + 1, barrelZ, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP), 2);
			put(camp, 2, y + 1, 0, Blocks.CAMPFIRE.getDefaultState().with(Properties.LIT, false));
			Direction.Axis axis = camp.fx() != 0 ? Direction.Axis.X : Direction.Axis.Z;
			BlockState log = Blocks.STRIPPED_SPRUCE_LOG.getDefaultState().with(Properties.AXIS, axis);
			put(camp, 2, y + 1, 2, log);
			put(camp, 2, y + 1, -2, log);
			put(camp, 0, y + 1, 2, Blocks.SOUL_LANTERN.getDefaultState());
			put(camp, 3, y + 1, -1, Blocks.SKELETON_SKULL.getDefaultState()
					.with(Properties.ROTATION, (int) (hash(camp.x(), y, camp.z(), 501) * 16)));
			// припорошено снегом
			for (int a = -3; a <= 3; a++) {
				for (int b = -3; b <= 3; b++) {
					if (a >= -3 && a <= -1 && Math.abs(b) <= 1) {
						continue;
					}
					int x = cx(camp, a, b);
					int z = cz(camp, a, b);
					if (inside(x, z) && hash(x, y, z, 502) < 0.35) {
						setIfAir(x, y + 1, z, Blocks.SNOW.getDefaultState());
					}
				}
			}
		}

		private static int cx(Camp camp, int a, int b) {
			return camp.x() + a * camp.fx() - b * camp.fz();
		}

		private static int cz(Camp camp, int a, int b) {
			return camp.z() + a * camp.fz() + b * camp.fx();
		}

		private void put(Camp camp, int a, int y, int b, BlockState state) {
			set(cx(camp, a, b), y, cz(camp, a, b), state);
		}

		private static Direction facing(Camp camp) {
			if (camp.fx() > 0) {
				return Direction.EAST;
			}
			if (camp.fx() < 0) {
				return Direction.WEST;
			}
			return camp.fz() > 0 ? Direction.SOUTH : Direction.NORTH;
		}

		/** Сундук или бочка - через мир, чтобы создалась блок-сущность с лутом. */
		private void container(int x, int y, int z, BlockState state, int salt) {
			if (!inside(x, z) || !validY(y)) {
				return;
			}
			BlockPos pos = new BlockPos(x, y, z);
			world.setBlockState(pos, state, 2);
			if (world.getBlockEntity(pos) instanceof LootableContainerBlockEntity container) {
				container.setLootTable(CAMP_LOOT, site.seed ^ (x * 31L + z * 17L + y + salt));
			}
		}

		// -------------------------------------------------------------- мелочи

		/** Кристаллы инея, сосульки, синие светильники в своде и снег на полу. */
		private void details() {
			BlockState icicle = ModBlocks.ICICLE.getDefaultState();
			for (int lx = 0; lx < 16; lx++) {
				for (int lz = 0; lz < 16; lz++) {
					int x = bx + lx;
					int z = bz + lz;
					ColumnInfo c = col(x, z);
					if (!c.cavity() || nearCamp(x, z, 4)) {
						continue;
					}
					// свод
					int top = c.ceil();
					if (get(x, top, z).isAir() && !get(x, top + 1, z).isAir()) {
						double h = hash(x, top, z, 601);
						boolean cluster = noise(x / 12.0, 9.0, z / 12.0) > -0.1;
						if (h < (cluster ? 0.05 : 0.012)) {
							set(x, top, z, crystal(Direction.DOWN, false));
						} else if (h < 0.1) {
							set(x, top, z, icicle);
							if (h < 0.075 && top - 1 > c.top() + 2 && get(x, top - 1, z).isAir()) {
								set(x, top - 1, z, icicle);
							}
						} else if (c.room() > 14 && hash(x, top + 1, z, 602) < 0.004) {
							// синий свет из толщи льда; до пола далеко, поэтому лёд и снег под ним не тают
							set(x, top + 1, z, Blocks.SEA_LANTERN.getDefaultState());
						}
					}
					// дно озера
					if (c.lake()) {
						int y = c.lakeFloor() + 1;
						if (y < c.top() && hash(x, y, z, 603) < 0.05 && get(x, y, z).isOf(Blocks.WATER)) {
							set(x, y, z, crystal(Direction.UP, true));
						}
						continue;
					}
					// пол
					BlockState floor = get(x, c.top(), z);
					if (!get(x, c.top() + 1, z).isAir()) {
						continue;
					}
					double h = hash(x, c.top(), z, 604);
					if (h < 0.014 && !floor.isOf(Blocks.POWDER_SNOW)) {
						set(x, c.top() + 1, z, crystal(Direction.UP, false));
					} else if (floor.isOf(Blocks.SNOW_BLOCK) && noise(x / 6.0, 31.0, z / 6.0) > -0.15) {
						int layers = Math.min(3, 1 + (int) (hash(x, c.top(), z, 605) * 2.4));
						set(x, c.top() + 1, z, Blocks.SNOW.getDefaultState().with(SnowBlock.LAYERS, layers));
					}
				}
			}
		}
	}
}
