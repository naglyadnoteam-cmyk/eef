package com.naglyadno.nedra.worldgen.feature;

import com.mojang.serialization.Codec;
import com.naglyadno.nedra.NedraMod;
import com.naglyadno.nedra.block.ModBlocks;
import com.naglyadno.nedra.worldgen.deep.DeepTerrain;
import com.naglyadno.nedra.worldgen.deep.DeepTerrain.Column;
import com.naglyadno.nedra.worldgen.deep.DeepTerrain.Layer;
import com.naglyadno.nedra.worldgen.deep.DeepTerrain.River;
import com.naglyadno.nedra.worldgen.deep.Settlements;
import com.naglyadno.nedra.worldgen.deep.Settlements.House;
import com.naglyadno.nedra.worldgen.deep.Settlements.Site;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.loot.LootTable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

import java.util.List;

/**
 * Первый шаг фич для недр: вырезает пещеры по {@link DeepTerrain}, прокладывает реки и озёра, отделывает стены
 * пещер породами своего биома и строит поселения. Пишет только в свой чанк, поэтому безопасен на шаге фич,
 * а непрерывность через границы чанков обеспечивает детерминированная функция формы недр.
 */
public class DeepTerrainFeature extends Feature<DefaultFeatureConfig> {

	private static final RegistryKey<LootTable> LOOT = RegistryKey.of(RegistryKeys.LOOT_TABLE,
			Identifier.of(NedraMod.MOD_ID, "chests/abandoned_settlement"));

	private static final int KEEP = 0, AIR = 1, WATER = 2, SOLID = 3;
	private static final int Y0 = DeepTerrain.FLOOR + 1;
	private static final int H = DeepTerrain.TOP - Y0;
	private static final int W = 18;

	private static final BlockState CAVE_AIR = Blocks.CAVE_AIR.getDefaultState();
	private static final BlockState WATER_STATE = Blocks.WATER.getDefaultState();
	private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.getDefaultState();

	public DeepTerrainFeature(Codec<DefaultFeatureConfig> codec) {
		super(codec);
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
		StructureWorldAccess world = context.getWorld();
		Chunk chunk = world.getChunk(context.getOrigin());
		if (chunk.getBottomY() >= DeepTerrain.TOP) {
			return false;
		}
		long seed = world.getSeed();
		DeepTerrain terrain = DeepTerrain.of(seed);
		ChunkPos pos = chunk.getPos();
		int bx = pos.getStartX();
		int bz = pos.getStartZ();

		Column[] columns = new Column[W * W];
		for (int lx = -1; lx <= 16; lx++) {
			for (int lz = -1; lz <= 16; lz++) {
				columns[(lx + 1) * W + (lz + 1)] = terrain.column(bx + lx, bz + lz);
			}
		}
		List<Site> sites = Settlements.near(seed, bx - 1, bz - 1, bx + 16, bz + 16, 10);
		byte[] codes = classify(terrain, columns, sites, bx, bz);

		write(chunk, codes);
		decorate(chunk, terrain, columns, codes, sites, seed, bx, bz);
		for (Site site : sites) {
			buildSettlement(world, chunk, site, bx, bz);
		}
		return true;
	}

	// ------------------------------------------------------------------ форма

	private static int index(int lx, int lz, int y) {
		return ((lx + 1) * W + (lz + 1)) * H + (y - Y0);
	}

	private static byte[] classify(DeepTerrain terrain, Column[] columns, List<Site> sites, int bx, int bz) {
		// узлы сетки 4x4x4 вокруг чанка с запасом на соседние колонны
		int gx0 = bx - 4;
		int gz0 = bz - 4;
		int gy0 = Math.floorDiv(Y0, 4) * 4;
		int ny = (DeepTerrain.TOP - gy0) / 4 + 2;
		double[][][] corners = new double[7][7][ny];
		for (int ix = 0; ix < 7; ix++) {
			for (int iz = 0; iz < 7; iz++) {
				int cx = gx0 + ix * 4;
				int cz = gz0 + iz * 4;
				Column column = terrain.column(cx, cz);
				for (int iy = 0; iy < ny; iy++) {
					corners[ix][iz][iy] = terrain.cornerDensity(column, cx, gy0 + iy * 4, cz);
				}
			}
		}

		byte[] codes = new byte[W * W * H];
		double[] c = new double[8];
		for (int lx = -1; lx <= 16; lx++) {
			for (int lz = -1; lz <= 16; lz++) {
				int x = bx + lx;
				int z = bz + lz;
				Column column = columns[(lx + 1) * W + (lz + 1)];
				boolean pillar = terrain.pillar(x, z);
				Site hall = null;
				boolean nearSite = false;
				for (Site site : sites) {
					if (site.roofHeight(x, z) > 0 || site.distance(x, z) < site.radius() + 1.5) {
						hall = site;
					}
					if (site.distance(x, z) < site.radius() + 10) {
						nearSite = true;
					}
				}
				River river1 = nearSite ? River.NONE : terrain.river(1, x, z);
				River river2 = nearSite ? River.NONE : terrain.river(2, x, z);
				int ix = Math.floorDiv(x - gx0, 4);
				int iz = Math.floorDiv(z - gz0, 4);
				double tx = (x - gx0 - ix * 4) / 4.0;
				double tz = (z - gz0 - iz * 4) / 4.0;
				for (int y = Y0; y < DeepTerrain.TOP; y++) {
					int code = KEEP;
					if (hall != null) {
						int roof = hall.roofHeight(x, z);
						if (y >= hall.floorY() && y < hall.floorY() + roof) {
							code = AIR;
						} else if (y >= hall.floorY() - 3 && y < hall.floorY()) {
							code = SOLID;
						}
					}
					if (code == KEEP) {
						code = riverCode(river1, y);
					}
					if (code == KEEP) {
						code = riverCode(river2, y);
					}
					if (code == KEEP) {
						int iy = Math.floorDiv(y - gy0, 4);
						double ty = (y - gy0 - iy * 4) / 4.0;
						c[0] = corners[ix][iz][iy];
						c[1] = corners[ix + 1][iz][iy];
						c[2] = corners[ix][iz][iy + 1];
						c[3] = corners[ix + 1][iz][iy + 1];
						c[4] = corners[ix][iz + 1][iy];
						c[5] = corners[ix + 1][iz + 1][iy];
						c[6] = corners[ix][iz + 1][iy + 1];
						c[7] = corners[ix + 1][iz + 1][iy + 1];
						if (trilinear(c, tx, ty, tz) > 0.0
								&& !(pillar && terrain.weight(column, Layer.ECHO, y) > 0.5)) {
							code = y < DeepTerrain.LAKE_LEVEL && terrain.weight(column, Layer.CRYSTAL, y) > 0.4 ? WATER : AIR;
						}
					}
					codes[index(lx, lz, y)] = (byte) code;
				}
			}
		}
		return codes;
	}

	private static int riverCode(River river, int y) {
		if (river.strength() <= 0.0) {
			return KEEP;
		}
		int water = river.waterY();
		int depth = river.depth();
		if (y >= water && y <= water + river.airHeight()) {
			return AIR;
		}
		if (y >= water - depth && y < water) {
			return WATER;
		}
		if (y >= water - depth - 2 && y < water - depth) {
			return SOLID;
		}
		return KEEP;
	}

	private static double trilinear(double[] c, double tx, double ty, double tz) {
		double x00 = c[0] + (c[1] - c[0]) * tx;
		double x10 = c[2] + (c[3] - c[2]) * tx;
		double x01 = c[4] + (c[5] - c[4]) * tx;
		double x11 = c[6] + (c[7] - c[6]) * tx;
		double y0 = x00 + (x10 - x00) * ty;
		double y1 = x01 + (x11 - x01) * ty;
		return y0 + (y1 - y0) * tz;
	}

	private static void write(Chunk chunk, byte[] codes) {
		for (int lx = 0; lx < 16; lx++) {
			for (int lz = 0; lz < 16; lz++) {
				for (int y = Y0; y < DeepTerrain.TOP; y++) {
					int code = codes[index(lx, lz, y)];
					if (code == KEEP) {
						continue;
					}
					ChunkSection section = section(chunk, y);
					BlockState current = section.getBlockState(lx, y & 15, lz);
					if (current.isOf(Blocks.BEDROCK)) {
						continue;
					}
					if (code == AIR && !current.isAir()) {
						section.setBlockState(lx, y & 15, lz, CAVE_AIR);
					} else if (code == WATER) {
						section.setBlockState(lx, y & 15, lz, WATER_STATE);
					} else if (code == SOLID && (current.isAir() || !current.getFluidState().isEmpty())) {
						section.setBlockState(lx, y & 15, lz, DEEPSLATE);
					}
				}
			}
		}
	}

	private static ChunkSection section(Chunk chunk, int y) {
		return chunk.getSection(chunk.getSectionIndex(y));
	}

	// ------------------------------------------------------------------ отделка пещер

	private static boolean open(Chunk chunk, byte[] codes, int lx, int lz, int y) {
		if (y < Y0 || y >= DeepTerrain.TOP) {
			return false;
		}
		int code = codes[index(lx, lz, y)];
		if (code == AIR || code == WATER) {
			return true;
		}
		if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16 && code == KEEP) {
			return section(chunk, y).getBlockState(lx, y & 15, lz).isAir();
		}
		return false;
	}

	private static boolean isRock(BlockState state) {
		return state.isOf(Blocks.DEEPSLATE) || state.isOf(Blocks.STONE) || state.isOf(Blocks.TUFF);
	}

	private static void decorate(Chunk chunk, DeepTerrain terrain, Column[] columns, byte[] codes, List<Site> sites,
			long seed, int bx, int bz) {
		for (int lx = 0; lx < 16; lx++) {
			for (int lz = 0; lz < 16; lz++) {
				int x = bx + lx;
				int z = bz + lz;
				boolean inSite = false;
				for (Site site : sites) {
					if (site.distance(x, z) < site.radius() + 2) {
						inSite = true;
					}
				}
				Column column = columns[(lx + 1) * W + (lz + 1)];
				for (int y = Y0 + 1; y < DeepTerrain.TOP - 1; y++) {
					if (open(chunk, codes, lx, lz, y)) {
						continue;
					}
					ChunkSection section = section(chunk, y);
					BlockState current = section.getBlockState(lx, y & 15, lz);
					if (!isRock(current)) {
						continue;
					}
					boolean floor = open(chunk, codes, lx, lz, y + 1);
					boolean ceiling = open(chunk, codes, lx, lz, y - 1);
					boolean wall = !floor && !ceiling && (open(chunk, codes, lx - 1, lz, y) || open(chunk, codes, lx + 1, lz, y)
							|| open(chunk, codes, lx, lz - 1, y) || open(chunk, codes, lx, lz + 1, y));
					if (!floor && !ceiling && !wall) {
						continue;
					}
					if (inSite && floor) {
						continue;
					}
					Layer layer = terrain.dominant(column, y);
					double r = DeepTerrain.hash01(seed, x, y, z, 31);
					double r2 = DeepTerrain.hash01(seed, x, y, z, 32);
					BlockState skin = floor ? floorBlock(layer, r) : ceiling ? ceilingBlock(layer, r) : wallBlock(layer, r);
					BlockState vein = caveVein(seed, layer, x, y, z);
					if (vein != null) {
						skin = vein;
					}
					if (skin != null) {
						section.setBlockState(lx, y & 15, lz, skin);
					}
					if (floor && !inSite) {
						BlockState above = floorDecoration(layer, skin, r2);
						placeIfAir(chunk, codes, lx, lz, y + 1, above);
					} else if (ceiling) {
						BlockState below = ceilingDecoration(layer, skin, r2);
						placeIfAir(chunk, codes, lx, lz, y - 1, below);
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ рудные жилы на стенах пещер

	/** Доля ячеек 8x8x8, через которые проходит открытая жила, и доля богатых жил (крупных). */
	private static final double VEIN_CHANCE = 0.075;
	private static final double RICH_VEIN_CHANCE = 0.012;

	private record VeinOre(BlockState state, int weight, double radius) {
	}

	private static final VeinOre[] VEINS_NONE = {
			vein(Blocks.DEEPSLATE_COAL_ORE, 30, 3.2), vein(Blocks.DEEPSLATE_IRON_ORE, 30, 2.8), vein(Blocks.DEEPSLATE_COPPER_ORE, 20, 3.0),
			vein(Blocks.DEEPSLATE_GOLD_ORE, 10, 2.2), vein(Blocks.DEEPSLATE_REDSTONE_ORE, 10, 2.4)};
	private static final VeinOre[] VEINS_ECHO = {
			vein(Blocks.DEEPSLATE_COAL_ORE, 25, 3.2), vein(Blocks.DEEPSLATE_IRON_ORE, 25, 2.8), vein(Blocks.DEEPSLATE_COPPER_ORE, 20, 3.0),
			vein(Blocks.DEEPSLATE_GOLD_ORE, 15, 2.4), vein(Blocks.DEEPSLATE_LAPIS_ORE, 15, 2.2)};
	private static final VeinOre[] VEINS_MAGNETIC = {
			vein(Blocks.DEEPSLATE_IRON_ORE, 40, 3.2), vein(Blocks.DEEPSLATE_REDSTONE_ORE, 25, 2.6), vein(Blocks.DEEPSLATE_GOLD_ORE, 15, 2.4),
			vein(Blocks.DEEPSLATE_COPPER_ORE, 10, 2.8), vein(ModBlocks.MAGNETITE_ORE, 10, 2.4)};
	private static final VeinOre[] VEINS_CRYSTAL = {
			vein(Blocks.DEEPSLATE_DIAMOND_ORE, 18, 1.7), vein(Blocks.DEEPSLATE_LAPIS_ORE, 25, 2.4), vein(Blocks.DEEPSLATE_REDSTONE_ORE, 20, 2.6),
			vein(Blocks.DEEPSLATE_GOLD_ORE, 15, 2.4), vein(Blocks.DEEPSLATE_EMERALD_ORE, 8, 1.6), vein(Blocks.DEEPSLATE_IRON_ORE, 14, 2.8)};
	/** Богатые жилы - только неценные руды: целая стена угля, железа или меди. */
	private static final VeinOre[] VEINS_RICH = {
			vein(Blocks.DEEPSLATE_COAL_ORE, 35, 4.8), vein(Blocks.DEEPSLATE_IRON_ORE, 35, 4.4), vein(Blocks.DEEPSLATE_COPPER_ORE, 25, 4.6),
			vein(Blocks.DEEPSLATE_GOLD_ORE, 5, 3.6)};

	private static VeinOre vein(Block block, int weight, double radius) {
		return new VeinOre(block.getDefaultState(), weight, radius);
	}

	/**
	 * Открытая рудная жила: мир поделён на ячейки 8x8x8, в небольшой части ячеек есть сгусток руды вокруг
	 * случайной точки. Здесь он проступает на поверхности пещеры (пол, стены, свод), поэтому руду видно
	 * прямо из прохода. Считается по координатам, так что жила непрерывна через границы чанков.
	 */
	private static BlockState caveVein(long seed, Layer layer, int x, int y, int z) {
		int cx = x >> 3;
		int cy = y >> 3;
		int cz = z >> 3;
		double roll = DeepTerrain.hash01(seed, cx, cy, cz, 41);
		if (roll >= VEIN_CHANCE) {
			return null;
		}
		VeinOre[] table = roll < RICH_VEIN_CHANCE ? VEINS_RICH : switch (layer) {
			case ECHO -> VEINS_ECHO;
			case MAGNETIC -> VEINS_MAGNETIC;
			case CRYSTAL -> VEINS_CRYSTAL;
			case NONE -> VEINS_NONE;
		};
		int total = 0;
		for (VeinOre ore : table) {
			total += ore.weight();
		}
		double pick = DeepTerrain.hash01(seed, cx, cy, cz, 42) * total;
		VeinOre ore = table[table.length - 1];
		for (VeinOre candidate : table) {
			pick -= candidate.weight();
			if (pick < 0) {
				ore = candidate;
				break;
			}
		}
		double ox = (cx << 3) + 1.5 + DeepTerrain.hash01(seed, cx, cy, cz, 43) * 5.0 - x;
		double oy = (cy << 3) + 1.5 + DeepTerrain.hash01(seed, cx, cy, cz, 44) * 5.0 - y;
		double oz = (cz << 3) + 1.5 + DeepTerrain.hash01(seed, cx, cy, cz, 45) * 5.0 - z;
		double radius = ore.radius();
		if (ox * ox + oy * oy * 1.6 + oz * oz > radius * radius) {
			return null;
		}
		return DeepTerrain.hash01(seed, x, y, z, 46) < 0.78 ? ore.state() : null;
	}

	private static void placeIfAir(Chunk chunk, byte[] codes, int lx, int lz, int y, BlockState state) {
		if (state == null || y < Y0 || y >= DeepTerrain.TOP || codes[index(lx, lz, y)] == WATER) {
			return;
		}
		ChunkSection section = section(chunk, y);
		if (section.getBlockState(lx, y & 15, lz).isAir()) {
			section.setBlockState(lx, y & 15, lz, state);
		}
	}

	private static BlockState floorBlock(Layer layer, double r) {
		return switch (layer) {
			case ECHO -> r < 0.45 ? Blocks.TUFF.getDefaultState() : r < 0.55 ? Blocks.GRAVEL.getDefaultState()
					: r < 0.64 ? Blocks.SCULK.getDefaultState() : null;
			case MAGNETIC -> r < 0.40 ? Blocks.SMOOTH_BASALT.getDefaultState() : r < 0.46 ? Blocks.MAGMA_BLOCK.getDefaultState()
					: r < 0.50 ? ModBlocks.MAGNETITE_ORE.getDefaultState() : r < 0.503 ? Blocks.RAW_IRON_BLOCK.getDefaultState() : null;
			case CRYSTAL -> r < 0.35 ? Blocks.CALCITE.getDefaultState() : r < 0.52 ? Blocks.AMETHYST_BLOCK.getDefaultState()
					: r < 0.54 ? Blocks.BUDDING_AMETHYST.getDefaultState() : r < 0.57 ? ModBlocks.LUMENITE_ORE.getDefaultState() : null;
			case NONE -> r < 0.22 ? Blocks.COBBLED_DEEPSLATE.getDefaultState() : r < 0.27 ? Blocks.GRAVEL.getDefaultState() : null;
		};
	}

	private static BlockState ceilingBlock(Layer layer, double r) {
		return switch (layer) {
			case ECHO -> r < 0.30 ? Blocks.TUFF.getDefaultState() : null;
			case MAGNETIC -> r < 0.40 ? Blocks.BASALT.getDefaultState() : r < 0.43 ? ModBlocks.MAGNETITE_ORE.getDefaultState() : null;
			case CRYSTAL -> r < 0.32 ? Blocks.AMETHYST_BLOCK.getDefaultState() : r < 0.37 ? ModBlocks.LUMENITE_ORE.getDefaultState()
					: r < 0.50 ? Blocks.CALCITE.getDefaultState() : null;
			case NONE -> null;
		};
	}

	private static BlockState wallBlock(Layer layer, double r) {
		return switch (layer) {
			case ECHO -> r < 0.35 ? Blocks.TUFF.getDefaultState() : null;
			case MAGNETIC -> r < 0.30 ? Blocks.SMOOTH_BASALT.getDefaultState() : r < 0.34 ? ModBlocks.MAGNETITE_ORE.getDefaultState() : null;
			case CRYSTAL -> r < 0.30 ? Blocks.CALCITE.getDefaultState() : r < 0.42 ? Blocks.AMETHYST_BLOCK.getDefaultState()
					: r < 0.45 ? ModBlocks.LUMENITE_ORE.getDefaultState() : null;
			case NONE -> null;
		};
	}

	private static BlockState floorDecoration(Layer layer, BlockState floor, double r) {
		if (layer == Layer.CRYSTAL && floor != null && floor.isOf(Blocks.AMETHYST_BLOCK) && r < 0.18) {
			return Blocks.AMETHYST_CLUSTER.getDefaultState().withIfExists(Properties.FACING, Direction.UP);
		}
		if (layer == Layer.ECHO && r < 0.006) {
			return Blocks.COBWEB.getDefaultState();
		}
		return null;
	}

	private static BlockState ceilingDecoration(Layer layer, BlockState ceiling, double r) {
		if (layer == Layer.CRYSTAL && ceiling != null && ceiling.isOf(Blocks.AMETHYST_BLOCK) && r < 0.14) {
			return Blocks.AMETHYST_CLUSTER.getDefaultState().withIfExists(Properties.FACING, Direction.DOWN);
		}
		if (layer == Layer.ECHO && r < 0.03) {
			return Blocks.COBWEB.getDefaultState();
		}
		if ((layer == Layer.NONE || layer == Layer.MAGNETIC) && r < 0.035) {
			return Blocks.GLOW_LICHEN.getDefaultState().withIfExists(Properties.UP, true);
		}
		return null;
	}

	// ------------------------------------------------------------------ поселения

	private static void put(Chunk chunk, int bx, int bz, int x, int y, int z, BlockState state) {
		int lx = x - bx;
		int lz = z - bz;
		if (lx < 0 || lx > 15 || lz < 0 || lz > 15 || y <= DeepTerrain.FLOOR || y >= DeepTerrain.TOP) {
			return;
		}
		section(chunk, y).setBlockState(lx, y & 15, lz, state);
	}

	private static boolean inChunk(int bx, int bz, int x, int z) {
		return x >= bx && x < bx + 16 && z >= bz && z < bz + 16;
	}

	private static void buildSettlement(StructureWorldAccess world, Chunk chunk, Site site, int bx, int bz) {
		int floor = site.floorY();
		// пол: площадь из плитки, улицы из булыжника и гравия
		for (int x = bx; x < bx + 16; x++) {
			for (int z = bz; z < bz + 16; z++) {
				double d = site.distance(x, z);
				if (d > site.radius() - 1) {
					continue;
				}
				double r = DeepTerrain.hash01(site.seed(), x, floor, z, 41);
				BlockState ground;
				if (d < 7) {
					ground = Blocks.DEEPSLATE_TILES.getDefaultState();
				} else if (Settlements.street(site, x, z)) {
					ground = r < 0.55 ? Blocks.COBBLED_DEEPSLATE.getDefaultState()
							: r < 0.8 ? Blocks.POLISHED_DEEPSLATE.getDefaultState() : Blocks.GRAVEL.getDefaultState();
				} else {
					ground = r < 0.85 ? DEEPSLATE : Blocks.TUFF.getDefaultState();
				}
				put(chunk, bx, bz, x, floor - 1, z, ground);
				// фонари на перекрёстках
				int ox = Math.floorMod(x - site.x() + Settlements.HOUSE_STEP / 2, Settlements.HOUSE_STEP);
				int oz = Math.floorMod(z - site.z() + Settlements.HOUSE_STEP / 2, Settlements.HOUSE_STEP);
				if (ox == 0 && oz == 0 && d > 9 && d < site.radius() - 4 && r < 0.5) {
					lampPost(chunk, bx, bz, x, floor, z);
				}
			}
		}
		// колодец и фонари на площади
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				int x = site.x() + dx;
				int z = site.z() + dz;
				if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
					put(chunk, bx, bz, x, floor - 1, z, WATER_STATE);
					put(chunk, bx, bz, x, floor - 2, z, Blocks.DEEPSLATE_BRICKS.getDefaultState());
				} else {
					put(chunk, bx, bz, x, floor, z, Blocks.DEEPSLATE_BRICK_WALL.getDefaultState());
				}
			}
		}
		for (int[] corner : new int[][]{{-5, -5}, {5, -5}, {-5, 5}, {5, 5}}) {
			lampPost(chunk, bx, bz, site.x() + corner[0], floor, site.z() + corner[1]);
		}
		// дома
		int span = site.radius() / Settlements.HOUSE_STEP + 1;
		for (int gx = -span; gx <= span; gx++) {
			for (int gz = -span; gz <= span; gz++) {
				House house = Settlements.house(site, gx, gz);
				if (house == null || house.cx() + house.half() < bx || house.cx() - house.half() > bx + 15
						|| house.cz() + house.half() < bz || house.cz() - house.half() > bz + 15) {
					continue;
				}
				buildHouse(world, chunk, site, house, bx, bz);
			}
		}
	}

	private static void lampPost(Chunk chunk, int bx, int bz, int x, int floor, int z) {
		put(chunk, bx, bz, x, floor, z, Blocks.POLISHED_DEEPSLATE.getDefaultState());
		put(chunk, bx, bz, x, floor + 1, z, Blocks.POLISHED_DEEPSLATE_WALL.getDefaultState());
		put(chunk, bx, bz, x, floor + 2, z, Blocks.POLISHED_DEEPSLATE_WALL.getDefaultState());
		put(chunk, bx, bz, x, floor + 3, z, ModBlocks.LUMENITE_LAMP.getDefaultState());
	}

	private static void buildHouse(StructureWorldAccess world, Chunk chunk, Site site, House house, int bx, int bz) {
		int floor = site.floorY();
		int h = house.half();
		long seed = site.seed() ^ (house.cx() * 31L + house.cz());
		for (int dx = -h; dx <= h; dx++) {
			for (int dz = -h; dz <= h; dz++) {
				int x = house.cx() + dx;
				int z = house.cz() + dz;
				if (!inChunk(bx, bz, x, z)) {
					continue;
				}
				boolean edgeX = Math.abs(dx) == h;
				boolean edgeZ = Math.abs(dz) == h;
				boolean wall = edgeX || edgeZ;
				put(chunk, bx, bz, x, floor - 1, z, Blocks.SPRUCE_PLANKS.getDefaultState());
				boolean roofHole = DeepTerrain.hash01(seed, dx, 1, dz, 51) < 0.05;
				put(chunk, bx, bz, x, floor + 4, z, dx == 0 && dz == 0 ? ModBlocks.LUMENITE_LAMP.getDefaultState()
						: roofHole ? CAVE_AIR : Blocks.DEEPSLATE_TILES.getDefaultState());
				for (int y = floor; y <= floor + 3; y++) {
					BlockState state;
					if (wall) {
						boolean doorSide = (house.doorDx() != 0 && dx == house.doorDx() * h && dz == 0)
								|| (house.doorDz() != 0 && dz == house.doorDz() * h && dx == 0);
						boolean windowSide = !doorSide && y == floor + 1 && ((edgeX && dz == 0) || (edgeZ && dx == 0));
						if (doorSide && y <= floor + 1) {
							state = CAVE_AIR;
						} else if (edgeX && edgeZ) {
							state = Blocks.POLISHED_DEEPSLATE.getDefaultState();
						} else if (windowSide && house.variant() > 0.25) {
							state = Blocks.GLASS.getDefaultState();
						} else if (DeepTerrain.hash01(seed, dx, y, dz, 52) < 0.04) {
							state = CAVE_AIR;
						} else {
							state = y == floor + 3 ? Blocks.DEEPSLATE_TILES.getDefaultState() : Blocks.DEEPSLATE_BRICKS.getDefaultState();
						}
					} else {
						boolean cornerTop = y == floor + 3 && Math.abs(dx) == h - 1 && Math.abs(dz) == h - 1;
						state = cornerTop && DeepTerrain.hash01(seed, dx, y, dz, 53) < 0.5 ? Blocks.COBWEB.getDefaultState() : CAVE_AIR;
					}
					put(chunk, bx, bz, x, y, z, state);
				}
			}
		}
		furnish(world, house, floor, bx, bz, seed);
	}

	/** Мебель у задней стены (напротив двери): сундук с припасами и рабочие блоки. */
	private static void furnish(StructureWorldAccess world, House house, int floor, int bx, int bz, long seed) {
		int h = house.half() - 1;
		int backDx = -house.doorDx();
		int backDz = -house.doorDz();
		Direction facing = house.doorDx() > 0 ? Direction.EAST : house.doorDx() < 0 ? Direction.WEST
				: house.doorDz() > 0 ? Direction.SOUTH : Direction.NORTH;
		BlockState[] pieces = {
				Blocks.CHEST.getDefaultState().withIfExists(Properties.HORIZONTAL_FACING, facing),
				Blocks.CRAFTING_TABLE.getDefaultState(),
				Blocks.FURNACE.getDefaultState().withIfExists(Properties.HORIZONTAL_FACING, facing),
				Blocks.BARREL.getDefaultState().withIfExists(Properties.FACING, Direction.UP),
				Blocks.BOOKSHELF.getDefaultState()};
		for (int along = -h; along <= h; along++) {
			int x = house.cx() + (backDx != 0 ? backDx * h : along);
			int z = house.cz() + (backDz != 0 ? backDz * h : along);
			if (!inChunk(bx, bz, x, z)) {
				continue;
			}
			double r = DeepTerrain.hash01(seed, along, 3, 0, 61);
			if (r > 0.75) {
				continue;
			}
			BlockState piece = along == 0 ? pieces[0] : pieces[1 + (int) (r * 4) % 4];
			BlockPos pos = new BlockPos(x, floor, z);
			world.setBlockState(pos, piece, Block.NOTIFY_LISTENERS);
			if (world.getBlockEntity(pos) instanceof LootableContainerBlockEntity container) {
				container.setLootTable(LOOT, seed ^ pos.asLong());
			}
		}
	}
}
