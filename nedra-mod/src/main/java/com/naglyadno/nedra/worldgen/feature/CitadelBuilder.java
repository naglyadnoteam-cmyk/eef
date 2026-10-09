package com.naglyadno.nedra.worldgen.feature;

import com.naglyadno.nedra.NedraMod;
import com.naglyadno.nedra.block.ModBlocks;
import com.naglyadno.nedra.entity.ModEntities;
import com.naglyadno.nedra.entity.PrismGaleEntity;
import com.naglyadno.nedra.worldgen.deep.Citadels;
import com.naglyadno.nedra.worldgen.deep.Citadels.Layout;
import com.naglyadno.nedra.worldgen.deep.Citadels.RoomType;
import com.naglyadno.nedra.worldgen.deep.Citadels.Site;
import com.naglyadno.nedra.worldgen.deep.DeepTerrain;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.loot.LootTable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.Chunk;

/**
 * Строит часть Хрустальной цитадели, попадающую в чанк. Все размеры и комнаты берутся из
 * {@link Citadels}; сюда попадают только блоки внутри чанка, поэтому чанки можно генерировать
 * в любом порядке и параллельно.
 */
final class CitadelBuilder {

	private static final RegistryKey<LootTable> VAULT_LOOT = loot("chests/citadel_vault");
	private static final RegistryKey<LootTable> HEART_LOOT = loot("chests/citadel_heart");
	private static final RegistryKey<LootTable> LIBRARY_LOOT = loot("chests/citadel_library");

	private static final BlockState BRICKS = ModBlocks.CRYSTAL_BRICKS.getDefaultState();
	private static final BlockState CHISELED = ModBlocks.CHISELED_CRYSTAL_BRICKS.getDefaultState();
	private static final BlockState TILES = ModBlocks.CRYSTAL_TILES.getDefaultState();
	private static final BlockState AIR = Blocks.CAVE_AIR.getDefaultState();
	private static final BlockState GLASS = Blocks.LIGHT_BLUE_STAINED_GLASS.getDefaultState();
	private static final BlockState PILLAR = Blocks.QUARTZ_PILLAR.getDefaultState();
	private static final BlockState LAMP = ModBlocks.LUMENITE_LAMP.getDefaultState();

	private final StructureWorldAccess world;
	private final Chunk chunk;
	private final int bx;
	private final int bz;

	private CitadelBuilder(StructureWorldAccess world, Chunk chunk, int bx, int bz) {
		this.world = world;
		this.chunk = chunk;
		this.bx = bx;
		this.bz = bz;
	}

	private static RegistryKey<LootTable> loot(String path) {
		return RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of(NedraMod.MOD_ID, path));
	}

	static void build(StructureWorldAccess world, Chunk chunk, Site site, int bx, int bz) {
		new CitadelBuilder(world, chunk, bx, bz).build(site);
	}

	// ------------------------------------------------------------------ примитивы (только внутри чанка)

	private boolean inside(int x, int z) {
		return x >= bx && x < bx + 16 && z >= bz && z < bz + 16;
	}

	private void set(int x, int y, int z, BlockState state) {
		if (inside(x, z) && y > chunk.getBottomY() && y < DeepTerrain.TOP) {
			chunk.getSection(chunk.getSectionIndex(y)).setBlockState(x & 15, y & 15, z & 15, state);
		}
	}

	private void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
		int fx1 = Math.max(Math.min(x1, x2), bx);
		int fx2 = Math.min(Math.max(x1, x2), bx + 15);
		int fz1 = Math.max(Math.min(z1, z2), bz);
		int fz2 = Math.min(Math.max(z1, z2), bz + 15);
		for (int x = fx1; x <= fx2; x++) {
			for (int z = fz1; z <= fz2; z++) {
				for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
					set(x, y, z, state);
				}
			}
		}
	}

	/** Блок с блок-сущностью (сундук, спавнер) - через мир, чтобы сущность создалась. */
	private boolean placeEntityBlock(int x, int y, int z, BlockState state) {
		if (!inside(x, z)) {
			return false;
		}
		world.setBlockState(new BlockPos(x, y, z), state, 2);
		return true;
	}

	private void chest(int x, int y, int z, Direction facing, RegistryKey<LootTable> loot, long seed) {
		if (placeEntityBlock(x, y, z, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, facing))
				&& world.getBlockEntity(new BlockPos(x, y, z)) instanceof LootableContainerBlockEntity container) {
			container.setLootTable(loot, seed ^ (x * 31L + z * 17L + y));
		}
	}

	private void barrel(int x, int y, int z, RegistryKey<LootTable> loot, long seed) {
		if (placeEntityBlock(x, y, z, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP))
				&& world.getBlockEntity(new BlockPos(x, y, z)) instanceof LootableContainerBlockEntity container) {
			container.setLootTable(loot, seed ^ (x * 13L + z * 7L + y));
		}
	}

	private void spawner(int x, int y, int z) {
		if (placeEntityBlock(x, y, z, Blocks.SPAWNER.getDefaultState())
				&& world.getBlockEntity(new BlockPos(x, y, z)) instanceof MobSpawnerBlockEntity spawner) {
			spawner.setEntityType(ModEntities.PRISM_GALE, world.getRandom());
		}
	}

	private void guard(double x, int y, double z, float yaw) {
		if (!inside((int) Math.floor(x), (int) Math.floor(z))) {
			return;
		}
		PrismGaleEntity gale = ModEntities.PRISM_GALE.create(world.toServerWorld(), SpawnReason.STRUCTURE);
		if (gale != null) {
			gale.refreshPositionAndAngles(x, y, z, yaw, 0.0F);
			gale.setPersistent();
			world.spawnEntityAndPassengers(gale);
		}
	}

	// ------------------------------------------------------------------ замок

	private void build(Site site) {
		Layout layout = Citadels.layout(site);
		int y0 = site.floorY();
		int top = y0 + Citadels.HEIGHT;
		int heartTop = top + Citadels.HEART_EXTRA;
		int hx1 = site.roomX(Citadels.HEART_MIN) - 1;
		int hx2 = site.roomX(Citadels.HEART_MAX) + Citadels.ROOM - 1;
		int hz1 = site.roomZ(Citadels.HEART_MIN) - 1;
		int hz2 = site.roomZ(Citadels.HEART_MAX) + Citadels.ROOM - 1;

		// корпус: сплошная кладка, в которой затем вырезаются комнаты
		fill(site.x0(), y0 - 2, site.z0(), site.maxX(), top + 1, site.maxZ(), BRICKS);
		fill(hx1, top + 2, hz1, hx2, heartTop + 1, hz2, BRICKS);
		fill(site.x0() + 1, y0 - 2, site.z0() + 1, site.maxX() - 1, y0 - 2, site.maxZ() - 1, Blocks.POLISHED_DEEPSLATE.getDefaultState());

		for (int i = 0; i < Citadels.GRID; i++) {
			for (int j = 0; j < Citadels.GRID; j++) {
				room(site, layout, i, j, y0, top);
			}
		}
		// Сердце: один высокий зал на четыре клетки
		heart(site, y0, heartTop, hx1, hz1, hx2, hz2);
		doors(site, layout, y0);
		entrance(site, y0);
		for (int i = 0; i < Citadels.GRID; i++) {
			for (int j = 0; j < Citadels.GRID; j++) {
				furnish(site, layout.types()[i][j], i, j, y0, top);
			}
		}
	}

	private void room(Site site, Layout layout, int i, int j, int y0, int top) {
		int x1 = site.roomX(i);
		int z1 = site.roomZ(j);
		int x2 = x1 + Citadels.ROOM - 2;
		int z2 = z1 + Citadels.ROOM - 2;
		fill(x1, y0, z1, x2, top - 1, z2, AIR);
		// пол: плитка, светящаяся вставка в центре и рамка из резного камня
		fill(x1, y0 - 1, z1, x2, y0 - 1, z2, TILES);
		int cx = x1 + 6;
		int cz = z1 + 6;
		fill(cx - 1, y0 - 1, cz - 1, cx + 1, y0 - 1, cz + 1, CHISELED);
		set(cx, y0 - 1, cz, Blocks.AMETHYST_BLOCK.getDefaultState());
		// свод: четыре светильника
		for (int dx : new int[]{3, 9}) {
			for (int dz : new int[]{3, 9}) {
				set(x1 + dx, top, z1 + dz, LAMP);
			}
		}
		// пилястры из кварцевых колонн у стен и витражи в стенах между комнатами
		for (int k = 3; k <= 9; k += 6) {
			fill(x1 + k, y0, z1, x1 + k, top - 1, z1, PILLAR);
			fill(x1 + k, y0, z2, x1 + k, top - 1, z2, PILLAR);
			fill(x1, y0, z1 + k, x1, top - 1, z1 + k, PILLAR);
			fill(x2, y0, z1 + k, x2, top - 1, z1 + k, PILLAR);
		}
		if (i + 1 < Citadels.GRID) {
			fill(x2 + 1, y0 + 2, z1 + 1, x2 + 1, y0 + 4, z1 + 1, GLASS);
			fill(x2 + 1, y0 + 2, z2 - 1, x2 + 1, y0 + 4, z2 - 1, GLASS);
		}
		if (j + 1 < Citadels.GRID) {
			fill(x1 + 1, y0 + 2, z2 + 1, x1 + 1, y0 + 4, z2 + 1, GLASS);
			fill(x2 - 1, y0 + 2, z2 + 1, x2 - 1, y0 + 4, z2 + 1, GLASS);
		}
	}

	private void heart(Site site, int y0, int heartTop, int hx1, int hz1, int hx2, int hz2) {
		fill(hx1 + 1, y0, hz1 + 1, hx2 - 1, heartTop - 1, hz2 - 1, AIR);
		fill(hx1 + 1, y0 - 1, hz1 + 1, hx2 - 1, y0 - 1, hz2 - 1, TILES);
		int cx = (hx1 + hx2) / 2;
		int cz = (hz1 + hz2) / 2;
		// мозаика пола: аметистовый крест и светящийся круг
		fill(cx - 9, y0 - 1, cz, cx + 9, y0 - 1, cz, Blocks.AMETHYST_BLOCK.getDefaultState());
		fill(cx, y0 - 1, cz - 9, cx, y0 - 1, cz + 9, Blocks.AMETHYST_BLOCK.getDefaultState());
		for (int dx = -6; dx <= 6; dx++) {
			for (int dz = -6; dz <= 6; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > 5.4 && d < 6.4) {
					set(cx + dx, y0 - 1, cz + dz, CHISELED);
				}
			}
		}
		// колоннада по кругу и светильники в своде
		for (int a = 0; a < 8; a++) {
			double ang = a * Math.PI / 4.0;
			int px = cx + (int) Math.round(Math.cos(ang) * 10);
			int pz = cz + (int) Math.round(Math.sin(ang) * 10);
			fill(px, y0, pz, px, heartTop - 1, pz, PILLAR);
			set(px, heartTop, pz, LAMP);
		}
		for (int dx = -8; dx <= 8; dx += 4) {
			for (int dz = -8; dz <= 8; dz += 4) {
				set(cx + dx, heartTop, cz + dz, LAMP);
			}
		}
		// большой кристалл в центре зала
		int crystalTop = heartTop - 4;
		for (int y = y0; y <= crystalTop; y++) {
			int r = y < y0 + 2 ? 2 : (y > crystalTop - 3 ? 0 : 1);
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.abs(dx) + Math.abs(dz) <= r + (r == 2 ? 1 : 0)) {
						BlockState state = (dx == 0 && dz == 0) ? CHISELED
								: ((dx + dz + y) & 1) == 0 ? Blocks.AMETHYST_BLOCK.getDefaultState() : GLASS;
						set(cx + dx, y, cz + dz, state);
					}
				}
			}
		}
		set(cx, crystalTop + 1, cz, Blocks.AMETHYST_CLUSTER.getDefaultState().with(Properties.FACING, Direction.UP));
		// награда: сундуки Сердца на постаментах по сторонам кристалла
		set(cx + 4, y0, cz, CHISELED);
		set(cx - 4, y0, cz, CHISELED);
		chest(cx + 4, y0 + 1, cz, Direction.WEST, HEART_LOOT, site.seed());
		chest(cx - 4, y0 + 1, cz, Direction.EAST, HEART_LOOT, site.seed());
		// стражи Сердца
		guard(cx + 0.5, y0, cz + 6.5, 180.0F);
		guard(cx + 0.5, y0, cz - 5.5, 0.0F);
		guard(cx + 6.5, y0, cz + 0.5, 90.0F);
	}

	/** Проёмы 3x4 между связанными комнатами с резной аркой. */
	private void doors(Site site, Layout layout, int y0) {
		for (int i = 0; i < Citadels.GRID; i++) {
			for (int j = 0; j < Citadels.GRID; j++) {
				if (i + 1 < Citadels.GRID && layout.east()[i][j] && !(Citadels.isHeart(i, j) && Citadels.isHeart(i + 1, j))) {
					int x = site.roomX(i + 1) - 1;
					int z = site.roomZ(j) + 6;
					fill(x, y0 + 4, z - 2, x, y0 + 4, z + 2, CHISELED);
					fill(x, y0, z - 1, x, y0 + 3, z + 1, AIR);
				}
				if (j + 1 < Citadels.GRID && layout.south()[i][j] && !(Citadels.isHeart(i, j) && Citadels.isHeart(i, j + 1))) {
					int x = site.roomX(i) + 6;
					int z = site.roomZ(j + 1) - 1;
					fill(x - 2, y0 + 4, z, x + 2, y0 + 4, z, CHISELED);
					fill(x - 1, y0, z, x + 1, y0 + 3, z, AIR);
				}
			}
		}
	}

	/** Вход: проём во внешней стене и туннель наружу, который обычно встречает пещеры недр. */
	private void entrance(Site site, int y0) {
		int[] room = Citadels.entranceRoom(site);
		int cx = site.roomX(room[0]) + 6;
		int cz = site.roomZ(room[1]) + 6;
		int dx = 0;
		int dz = 0;
		int wx = cx;
		int wz = cz;
		switch (site.entranceSide()) {
			case 0 -> {
				dz = -1;
				wz = site.z0();
			}
			case 1 -> {
				dx = 1;
				wx = site.maxX();
			}
			case 2 -> {
				dz = 1;
				wz = site.maxZ();
			}
			default -> {
				dx = -1;
				wx = site.x0();
			}
		}
		for (int s = 0; s <= Citadels.TUNNEL; s++) {
			int x = wx + dx * s;
			int z = wz + dz * s;
			int px = dz != 0 ? 1 : 0;
			int pz = dx != 0 ? 1 : 0;
			fill(x - px * 2, y0 - 1, z - pz * 2, x + px * 2, y0 + 4, z + pz * 2, s == 0 ? CHISELED : BRICKS);
			fill(x - px, y0, z - pz, x + px, y0 + 3, z + pz, AIR);
			fill(x - px, y0 - 1, z - pz, x + px, y0 - 1, z + pz, s % 6 == 3 ? CHISELED : TILES);
			if (s % 6 == 3) {
				set(x, y0 + 4, z, LAMP);
			}
		}
	}

	// ------------------------------------------------------------------ убранство комнат

	private void furnish(Site site, RoomType type, int i, int j, int y0, int top) {
		int x1 = site.roomX(i);
		int z1 = site.roomZ(j);
		int x2 = x1 + Citadels.ROOM - 2;
		int z2 = z1 + Citadels.ROOM - 2;
		int cx = x1 + 6;
		int cz = z1 + 6;
		long seed = site.seed() + i * 7919L + j * 104729L;
		switch (type) {
			case HALL -> {
				for (int dx : new int[]{3, 9}) {
					for (int dz : new int[]{3, 9}) {
						fill(x1 + dx, y0, z1 + dz, x1 + dx, top - 1, z1 + dz, PILLAR);
					}
				}
				corners(x1, z1, x2, z2, y0, Blocks.AMETHYST_CLUSTER.getDefaultState().with(Properties.FACING, Direction.UP));
			}
			case VAULT -> {
				// ряд сундуков у северной стены (проход в центре стены свободен) и бочки у южной
				for (int dx : new int[]{2, 4, 8, 10}) {
					set(x1 + dx, y0, z1 + 1, CHISELED);
					chest(x1 + dx, y0 + 1, z1 + 1, Direction.SOUTH, VAULT_LOOT, seed);
				}
				barrel(x1 + 2, y0, z2 - 1, VAULT_LOOT, seed + 1);
				barrel(x2 - 2, y0, z2 - 1, VAULT_LOOT, seed + 2);
				set(x1 + 1, y0, z1 + 1, Blocks.RAW_GOLD_BLOCK.getDefaultState());
				set(x2 - 1, y0, z1 + 1, Blocks.RAW_IRON_BLOCK.getDefaultState());
			}
			case GUARD -> {
				// спавнер стража на резном постаменте в кольце из стекла
				set(cx, y0, cz, CHISELED);
				spawner(cx, y0 + 1, cz);
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) == 2 && (dx + dz) % 2 == 0) {
							set(cx + dx, y0, cz + dz, GLASS);
						}
					}
				}
				barrel(x2 - 1, y0, z1 + 1, VAULT_LOOT, seed + 3);
			}
			case LIBRARY -> {
				for (int x = x1 + 1; x <= x2 - 1; x++) {
					if (x == cx - 1 || x == cx || x == cx + 1) {
						continue;
					}
					fill(x, y0, z1, x, y0 + 2, z1, Blocks.BOOKSHELF.getDefaultState());
					fill(x, y0, z2, x, y0 + 2, z2, Blocks.BOOKSHELF.getDefaultState());
				}
				set(cx, y0, cz, Blocks.LECTERN.getDefaultState());
				// у дверей ничего не ставим: сундук и стол - в стороне от проходов
				chest(x1 + 1, y0, z1 + 2, Direction.EAST, LIBRARY_LOOT, seed);
				if (DeepTerrain.hash01(seed, i, 5, j, 951) < 0.35) {
					set(x2 - 1, y0, z2 - 2, Blocks.ENCHANTING_TABLE.getDefaultState());
				}
			}
			case GARDEN -> {
				// пруд с подсветкой, мох и грибы вокруг, друзы в углах
				fill(cx - 2, y0 - 1, cz - 2, cx + 2, y0 - 1, cz + 2, Blocks.WATER.getDefaultState());
				set(cx, y0 - 2, cz, Blocks.SEA_LANTERN.getDefaultState());
				for (int dx = -3; dx <= 3; dx++) {
					for (int dz = -3; dz <= 3; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) == 3) {
							set(cx + dx, y0 - 1, cz + dz, Blocks.MOSS_BLOCK.getDefaultState());
							if (((dx + dz) & 1) == 0) {
								set(cx + dx, y0, cz + dz, ModBlocks.GLOWCAP.getDefaultState());
							}
						}
					}
				}
				corners(x1, z1, x2, z2, y0, ModBlocks.SCARLET_CLUSTER.getDefaultState().with(Properties.FACING, Direction.UP));
			}
			case ENTRANCE -> {
				set(cx, y0, cz, CHISELED);
				set(cx, y0 + 1, cz, Blocks.AMETHYST_CLUSTER.getDefaultState().with(Properties.FACING, Direction.UP));
			}
			case HEART -> {
				// убранство Сердца строится отдельно
			}
		}
	}

	private void corners(int x1, int z1, int x2, int z2, int y, BlockState state) {
		set(x1 + 1, y, z1 + 1, state);
		set(x2 - 1, y, z1 + 1, state);
		set(x1 + 1, y, z2 - 1, state);
		set(x2 - 1, y, z2 - 1, state);
	}
}
