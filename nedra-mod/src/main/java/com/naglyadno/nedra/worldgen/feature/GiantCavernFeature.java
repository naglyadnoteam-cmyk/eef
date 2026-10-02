package com.naglyadno.nedra.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.naglyadno.nedra.worldgen.BiomePainter;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.FeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;

import java.util.Optional;

/**
 * Высекает крупную эллипсоидную каверну с кристаллическими вкраплениями по краям,
 * либо (в режиме "ocean") затапливает такую же полость - редкий подземный океан.
 */
public class GiantCavernFeature extends Feature<GiantCavernFeature.Config> {

	public GiantCavernFeature(Codec<Config> codec) {
		super(codec);
	}

	@Override
	public boolean generate(FeatureContext<Config> context) {
		StructureWorldAccess world = context.getWorld();
		Random random = context.getRandom();
		BlockPos origin = context.getOrigin();
		Config config = context.getConfig();

		if (origin.getY() < world.getBottomY() + 8) {
			return false;
		}

		// Жёсткий потолок радиуса: запись блоков далеко за пределами чанка, в
		// котором появилась точка фичи, не гарантированно безопасна во время
		// шага "features" генерации, поэтому каверна не может выйти за пределы
		// соседнего чанка независимо от того, что указано в конфиге.
		int safeMaxRadius = 14;
		int clampedMax = Math.min(config.maxRadius(), safeMaxRadius);
		int clampedMin = Math.min(config.minRadius(), clampedMax);
		int radiusXZ = clampedMin + random.nextInt(Math.max(1, clampedMax - clampedMin + 1));
		int radiusY = Math.max(3, (int) (radiusXZ * 0.65));
		BlockState fill = config.ocean() ? Blocks.WATER.getDefaultState() : Blocks.CAVE_AIR.getDefaultState();

		for (int dx = -radiusXZ; dx <= radiusXZ; dx++) {
			for (int dz = -radiusXZ; dz <= radiusXZ; dz++) {
				for (int dy = -radiusY; dy <= radiusY; dy++) {
					double nx = dx / (double) radiusXZ;
					double ny = dy / (double) radiusY;
					double nz = dz / (double) radiusXZ;
					double dist = nx * nx + ny * ny + nz * nz;
					if (dist > 1.0) {
						continue;
					}
					BlockPos pos = origin.add(dx, dy, dz);
					BlockState current = world.getBlockState(pos);
					if (current.isAir() || current.isOf(Blocks.BEDROCK)) {
						continue;
					}
					if (!config.ocean() && dist > 0.80 && random.nextFloat() < config.crystalChance()) {
						world.setBlockState(pos, pickCrystalState(random), Block.NOTIFY_LISTENERS);
					} else {
						world.setBlockState(pos, fill, Block.NOTIFY_LISTENERS);
					}
				}
			}
		}

		if (config.biomeId().isPresent()) {
			RegistryKey<Biome> biomeKey = RegistryKey.of(RegistryKeys.BIOME, config.biomeId().get());
			BlockBox box = BlockBox.create(
					origin.add(-radiusXZ, -radiusY, -radiusXZ),
					origin.add(radiusXZ, radiusY, radiusXZ));
			BiomePainter.queue(box, biomeKey);
		}

		return true;
	}

	private BlockState pickCrystalState(Random random) {
		float f = random.nextFloat();
		if (f < 0.1f) {
			return Blocks.BUDDING_AMETHYST.getDefaultState();
		}
		if (f < 0.4f) {
			return Blocks.AMETHYST_BLOCK.getDefaultState();
		}
		return Blocks.AMETHYST_CLUSTER.getDefaultState();
	}

	public record Config(int minRadius, int maxRadius, boolean ocean, float crystalChance,
			Optional<Identifier> biomeId) implements FeatureConfig {
		public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.fieldOf("min_radius").forGetter(Config::minRadius),
				Codec.INT.fieldOf("max_radius").forGetter(Config::maxRadius),
				Codec.BOOL.fieldOf("ocean").forGetter(Config::ocean),
				Codec.FLOAT.fieldOf("crystal_chance").forGetter(Config::crystalChance),
				Identifier.CODEC.optionalFieldOf("biome").forGetter(Config::biomeId)
		).apply(instance, Config::new));
	}
}
