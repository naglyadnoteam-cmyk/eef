package com.naglyadno.nedra.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.naglyadno.nedra.NedraMod;
import com.naglyadno.nedra.worldgen.BiomePainter;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
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

	private void decorateRoom(StructureWorldAccess world, Random random, BlockPos center) {
		placeIfOpen(world, center.up(1), Blocks.CRAFTING_TABLE.getDefaultState());
		placeIfOpen(world, center.add(2, 1, 0), Blocks.FURNACE.getDefaultState());
		placeIfOpen(world, center.add(-2, 1, 0), Blocks.CHEST.getDefaultState());
		for (Direction direction : HORIZONTALS) {
			if (random.nextFloat() < 0.6f) {
				BlockPos pos = center.offset(direction, 3).up(2);
				placeIfOpen(world, pos, Blocks.TORCH.getDefaultState());
			}
		}
	}

	private void setIfNotBedrock(StructureWorldAccess world, BlockPos pos, BlockState state) {
		if (!world.getBlockState(pos).isOf(Blocks.BEDROCK)) {
			world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
		}
	}

	private void placeIfOpen(StructureWorldAccess world, BlockPos pos, BlockState state) {
		BlockState current = world.getBlockState(pos);
		if (current.isAir() || current.isOf(Blocks.CAVE_AIR)) {
			world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
		}
	}

	public record Config(int rooms) implements FeatureConfig {
		public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.fieldOf("rooms").forGetter(Config::rooms)
		).apply(instance, Config::new));
	}
}
