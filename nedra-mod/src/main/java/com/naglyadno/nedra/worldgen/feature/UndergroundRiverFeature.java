package com.naglyadno.nedra.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.FeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * Прокладывает подземную реку случайным 3D-блужданием: в отличие от обычных
 * пещерных ручьёв, она может подниматься и опускаться, а не течь строго горизонтально.
 */
public class UndergroundRiverFeature extends Feature<UndergroundRiverFeature.Config> {

	public UndergroundRiverFeature(Codec<Config> codec) {
		super(codec);
	}

	@Override
	public boolean generate(FeatureContext<Config> context) {
		StructureWorldAccess world = context.getWorld();
		Random random = context.getRandom();
		BlockPos origin = context.getOrigin();
		Config config = context.getConfig();

		double x = origin.getX();
		double y = origin.getY();
		double z = origin.getZ();

		double dx = random.nextDouble() - 0.5;
		double dy = (random.nextDouble() - 0.5) * 0.4;
		double dz = random.nextDouble() - 0.5;
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 0.0001) {
			len = 1.0;
		}
		dx /= len;
		dy /= len;
		dz /= len;

		BlockState water = Blocks.WATER.getDefaultState();
		int bottomLimit = world.getBottomY() + 6;
		int radius = config.radius();

		// Высекание блоков далеко за пределами стартового чанка во время шага
		// "features" некорректно (ванильная генерация не гарантирует, что такой
		// соседний чанк ещё не сохранён) - поэтому блуждание жёстко ограничено
		// небольшим радиусом вокруг точки появления.
		double maxHorizontalReach = 12.0;

		for (int step = 0; step < config.length(); step++) {
			dx += (random.nextDouble() - 0.5) * 0.35;
			dy += (random.nextDouble() - 0.5) * 0.25;
			dz += (random.nextDouble() - 0.5) * 0.35;
			double norm = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (norm < 0.0001) {
				norm = 1.0;
			}
			dx /= norm;
			dy /= norm;
			dz /= norm;

			double nextX = x + dx * 1.5;
			double nextZ = z + dz * 1.5;
			if (nextX - origin.getX() > maxHorizontalReach || nextX - origin.getX() < -maxHorizontalReach) {
				dx = -dx;
				nextX = x + dx * 1.5;
			}
			if (nextZ - origin.getZ() > maxHorizontalReach || nextZ - origin.getZ() < -maxHorizontalReach) {
				dz = -dz;
				nextZ = z + dz * 1.5;
			}
			x = nextX;
			z = nextZ;
			y += dy * 1.5;

			if (y < bottomLimit) {
				y = bottomLimit;
				dy = Math.abs(dy);
			}

			BlockPos center = BlockPos.ofFloored(x, y, z);
			for (int ddx = -radius; ddx <= radius; ddx++) {
				for (int ddy = -radius; ddy <= radius; ddy++) {
					for (int ddz = -radius; ddz <= radius; ddz++) {
						double nx = ddx / (double) radius;
						double ny = ddy / (double) radius;
						double nz = ddz / (double) radius;
						if (nx * nx + ny * ny + nz * nz > 1.0) {
							continue;
						}
						BlockPos pos = center.add(ddx, ddy, ddz);
						BlockState current = world.getBlockState(pos);
						if (current.isOf(Blocks.BEDROCK)) {
							continue;
						}
						world.setBlockState(pos, water, Block.NOTIFY_LISTENERS);
					}
				}
			}
		}

		return true;
	}

	public record Config(int length, int radius) implements FeatureConfig {
		public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.fieldOf("length").forGetter(Config::length),
				Codec.INT.fieldOf("radius").forGetter(Config::radius)
		).apply(instance, Config::new));
	}
}
