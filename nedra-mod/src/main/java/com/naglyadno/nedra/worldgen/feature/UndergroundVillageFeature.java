package com.naglyadno.nedra.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.naglyadno.nedra.NedraMod;
import com.naglyadno.nedra.block.ModBlocks;
import com.naglyadno.nedra.worldgen.BiomePainter;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.FurnaceBlock;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.loot.LootTable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.FeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * Высекает цепочку небольших залов - заброшенное подземное поселение,
 * обставленное мебелью, но без живых жителей (отсюда "эхо" в названии биома).
 */
public class UndergroundVillageFeature extends Feature<UndergroundVillageFeature.Config> {

	private static final Direction[] HORIZONTALS = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
	private static final BlockState FLOOR = Blocks.POLISHED_DEEPSLATE.getDefaultState();
	private static final RegistryKey<LootTable> LOOT_TABLE =
			RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of(NedraMod.MOD_ID, "chests/abandoned_settlement"));

	public UndergroundVillageFeature(Codec<Config> codec) {
		super(codec);
	}

	@Override
	public boolean generate(FeatureContext<Config> context) {
		StructureWorldAccess world = context.getWorld();
		Random random = context.getRandom();
		BlockPos origin = context.getOrigin();
		Config config = context.getConfig();

		BlockPos cursor = origin;
		Direction facing = HORIZONTALS[random.nextInt(HORIZONTALS.length)];

		BlockPos min = cursor;
		BlockPos max = cursor;

		// Как и в случае с рекой: держим всю цепочку залов внутри небольшого
		// радиуса от точки появления, чтобы не записывать блоки в чанк, который
		// генератор уже не считает "соседним" для текущего шага генерации.
		int maxHorizontalReach = 14;

		for (int i = 0; i < config.rooms(); i++) {
			carveRoom(world, random, cursor);
			min = minOf(min, cursor);
			max = maxOf(max, cursor);

			int tunnelLength = 3 + random.nextInt(3);
			for (int t = 0; t < tunnelLength; t++) {
				BlockPos next = cursor.offset(facing);
				if (Math.abs(next.getX() - origin.getX()) > maxHorizontalReach
						|| Math.abs(next.getZ() - origin.getZ()) > maxHorizontalReach) {
					facing = facing.getOpposite();
					next = cursor.offset(facing);
				}
				cursor = next;
				carveTunnel(world, cursor, facing);
			}
			if (random.nextFloat() < 0.4f) {
				facing = HORIZONTALS[random.nextInt(HORIZONTALS.length)];
			}
		}

		RegistryKey<Biome> biomeKey = RegistryKey.of(RegistryKeys.BIOME, Identifier.of(NedraMod.MOD_ID, "echo_hollows"));
		BlockBox box = BlockBox.create(min.add(-6, -4, -6), max.add(6, 6, 6));
		BiomePainter.queue(box, biomeKey);

		return true;
	}

	private BlockPos minOf(BlockPos a, BlockPos b) {
		return new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
	}

	private BlockPos maxOf(BlockPos a, BlockPos b) {
		return new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
	}

	private void carveTunnel(StructureWorldAccess world, BlockPos center, Direction facing) {
		Direction side = facing.rotateYClockwise();
		for (int w = -1; w <= 1; w++) {
			for (int h = 0; h <= 2; h++) {
				BlockPos pos = center.offset(side, w).up(h);
				setIfNotBedrock(world, pos, h == 0 ? FLOOR : Blocks.CAVE_AIR.getDefaultState());
			}
		}
	}

	private void carveRoom(StructureWorldAccess world, Random random, BlockPos center) {
		int rx = 3;
		int rz = 3;
		int ry = 3;
		for (int dx = -rx; dx <= rx; dx++) {
			for (int dz = -rz; dz <= rz; dz++) {
				for (int dy = 0; dy <= ry; dy++) {
					BlockPos pos = center.add(dx, dy, dz);
					boolean edge = Math.abs(dx) == rx || Math.abs(dz) == rz || dy == ry;
					if (dy == 0) {
						setIfNotBedrock(world, pos, FLOOR);
					} else if (!edge) {
						setIfNotBedrock(world, pos, Blocks.CAVE_AIR.getDefaultState());
					}
				}
			}
		}
		decorateRoom(world, random, center);
	}

	private static void assignLoot(StructureWorldAccess world, Random random, BlockPos pos) {
		if (world.getBlockEntity(pos) instanceof LootableContainerBlockEntity container) {
			container.setLootTable(LOOT_TABLE, random.nextLong());
		}
	}

	private void decorateRoom(StructureWorldAccess world, Random random, BlockPos center) {
		// мебель вдоль одной из стен, чтобы проход через центр зала оставался свободным
		Direction wall = HORIZONTALS[random.nextInt(HORIZONTALS.length)];
		Direction along = wall.rotateYClockwise();
		BlockPos base = center.offset(wall, 2).up();
		placeIfOpen(world, base.offset(along, -1), Blocks.CRAFTING_TABLE.getDefaultState());
		placeIfOpen(world, base, Blocks.FURNACE.getDefaultState().with(FurnaceBlock.FACING, wall.getOpposite()));
		BlockPos chestPos = base.offset(along, 1);
		if (placeIfOpen(world, chestPos, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, wall.getOpposite()))) {
			assignLoot(world, random, chestPos);
		}
		if (random.nextFloat() < 0.4f) {
			BlockPos barrel = center.offset(wall.getOpposite(), 2).offset(along, 2).up();
			if (placeIfOpen(world, barrel, Blocks.BARREL.getDefaultState())) {
				assignLoot(world, random, barrel);
			}
		}

		// свет: люменитовый светильник в своде или настенный факел
		BlockPos ceiling = center.up(3);
		if (random.nextFloat() < 0.6f && !world.getBlockState(ceiling).isAir()) {
			world.setBlockState(ceiling, ModBlocks.LUMENITE_LAMP.getDefaultState(), Block.NOTIFY_LISTENERS);
		} else {
			Direction torchWall = wall.getOpposite();
			BlockPos torchPos = center.offset(torchWall, 2).up(2);
			if (world.getBlockState(torchPos.offset(torchWall)).isSolidBlock(world, torchPos.offset(torchWall))) {
				placeIfOpen(world, torchPos, Blocks.WALL_TORCH.getDefaultState().with(WallTorchBlock.FACING, wall));
			}
		}

		// заброшенность: паутина под сводом по углам
		for (int i = 0; i < 2; i++) {
			if (random.nextFloat() < 0.5f) {
				int sx = random.nextBoolean() ? 2 : -2;
				int sz = random.nextBoolean() ? 2 : -2;
				placeIfOpen(world, center.add(sx, 2, sz), Blocks.COBWEB.getDefaultState());
			}
		}
	}

	private void setIfNotBedrock(StructureWorldAccess world, BlockPos pos, BlockState state) {
		if (!world.getBlockState(pos).isOf(Blocks.BEDROCK)) {
			world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
		}
	}

	private boolean placeIfOpen(StructureWorldAccess world, BlockPos pos, BlockState state) {
		BlockState current = world.getBlockState(pos);
		if (current.isAir()) {
			world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
			return true;
		}
		return false;
	}

	public record Config(int rooms) implements FeatureConfig {
		public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.fieldOf("rooms").forGetter(Config::rooms)
		).apply(instance, Config::new));
	}
}
