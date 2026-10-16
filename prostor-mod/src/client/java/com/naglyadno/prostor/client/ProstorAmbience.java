package com.naglyadno.prostor.client;

import com.naglyadno.prostor.worldgen.ProstorBiomes;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.util.HashMap;
import java.util.Map;

/**
 * Лёгкие эффекты воды без шейдеров, только частицами и только на клиенте:
 * <ul>
 * <li>в поймах и каньонах по поверхности реки бегут мелкие волны в сторону течения;</li>
 * <li>у водопадов - брызги и водяная пыль там, где падающая вода встречает озеро.</li>
 * </ul>
 * Зелёная дымка болот задана в самом биоме (туман, небо, облака, светящиеся огоньки).
 */
public final class ProstorAmbience {

	private static final double[] NO_FLOW = {0, 0};
	/** Направление течения для участков 8x8 блоков (кэш, считается по блокам воды вокруг). */
	private static final Map<Long, double[]> FLOW = new HashMap<>();

	private ProstorAmbience() {
	}

	public static void reset() {
		FLOW.clear();
	}

	public static void tick(MinecraftClient client) {
		ClientWorld world = client.world;
		if (world == null || client.player == null || client.isPaused() || world.getRegistryKey() != World.OVERWORLD) {
			return;
		}
		BlockPos camera = client.gameRenderer.getCamera().getBlockPos();
		RegistryEntry<Biome> biome = world.getBiome(camera);
		Random random = world.getRandom();
		if (biome.matchesKey(ProstorBiomes.FLOODPLAIN) || biome.matchesKey(ProstorBiomes.RIVER_CANYON)) {
			for (int i = 0; i < 4; i++) {
				ripple(world, camera, random);
			}
		} else if (biome.matchesKey(ProstorBiomes.WATERFALL_CLIFFS)) {
			for (int i = 0; i < 6; i++) {
				mist(world, camera, random);
			}
		}
		if (FLOW.size() > 2048) {
			FLOW.clear();
		}
	}

	/** Верх стоячей воды в столбце (вода, над ней воздух) рядом с высотой камеры или Integer.MIN_VALUE. */
	private static int waterSurface(ClientWorld world, int x, int z, int fromY, BlockPos.Mutable pos) {
		for (int y = fromY + 8; y >= fromY - 24; y--) {
			BlockState state = world.getBlockState(pos.set(x, y, z));
			if (state.isAir()) {
				continue;
			}
			FluidState fluid = state.getFluidState();
			return fluid.isOf(Fluids.WATER) ? y : Integer.MIN_VALUE;
		}
		return Integer.MIN_VALUE;
	}

	private static boolean isSurface(ClientWorld world, int x, int y, int z, BlockPos.Mutable pos) {
		return world.getFluidState(pos.set(x, y, z)).isOf(Fluids.WATER) && world.getBlockState(pos.set(x, y + 1, z)).isAir();
	}

	/** Волна на поверхности реки, бегущая по течению. */
	private static void ripple(ClientWorld world, BlockPos camera, Random random) {
		BlockPos.Mutable pos = new BlockPos.Mutable();
		int x = camera.getX() + random.nextInt(33) - 16;
		int z = camera.getZ() + random.nextInt(33) - 16;
		int y = waterSurface(world, x, z, camera.getY(), pos);
		if (y == Integer.MIN_VALUE) {
			return;
		}
		double[] flow = FLOW.computeIfAbsent(((long) (x >> 3) << 32) ^ ((z >> 3) & 0xFFFFFFFFL) ^ ((long) y << 52),
				key -> direction(world, x, y, z));
		if (flow == NO_FLOW) {
			return;
		}
		double speed = 0.05 + random.nextDouble() * 0.04;
		world.addParticleClient(ParticleTypes.FISHING, x + random.nextDouble(), y + 0.92, z + random.nextDouble(),
				flow[0] * speed, 0.0, flow[1] * speed);
	}

	/**
	 * Ось реки: направление, в котором вода тянется дальше всего в обе стороны. Знак течения выбран
	 * одинаковым для всего мира (к «югу-востоку»), поэтому соседние участки одной реки текут согласованно.
	 */
	private static double[] direction(ClientWorld world, int x, int y, int z) {
		BlockPos.Mutable pos = new BlockPos.Mutable();
		double bestA = 0;
		int bestScore = 0;
		for (int i = 0; i < 16; i++) {
			double a = Math.PI * i / 16;
			int score = 0;
			for (int d = 3; d <= 12; d += 3) {
				int fx = (int) Math.floor(x + Math.cos(a) * d);
				int fz = (int) Math.floor(z + Math.sin(a) * d);
				int bx = (int) Math.floor(x - Math.cos(a) * d);
				int bz = (int) Math.floor(z - Math.sin(a) * d);
				if (isSurface(world, fx, y, fz, pos)) {
					score++;
				}
				if (isSurface(world, bx, y, bz, pos)) {
					score++;
				}
			}
			if (score > bestScore) {
				bestScore = score;
				bestA = a;
			}
		}
		if (bestScore < 6) {
			return NO_FLOW; // это не русло, а озеро или лужа
		}
		double dx = Math.cos(bestA);
		double dz = Math.sin(bestA);
		if (dx * 0.6 + dz * 0.8 < 0) {
			dx = -dx;
			dz = -dz;
		}
		return new double[]{dx, dz};
	}

	/** Брызги и водяная пыль у подножия водопада. */
	private static void mist(ClientWorld world, BlockPos camera, Random random) {
		BlockPos.Mutable pos = new BlockPos.Mutable();
		int x = camera.getX() + random.nextInt(29) - 14;
		int z = camera.getZ() + random.nextInt(29) - 14;
		for (int y = camera.getY() + 16; y >= camera.getY() - 24; y--) {
			FluidState fluid = world.getFluidState(pos.set(x, y, z));
			if (!fluid.isOf(Fluids.FLOWING_WATER) || !fluid.contains(FlowableFluid.FALLING) || !fluid.get(FlowableFluid.FALLING)) {
				continue;
			}
			FluidState below = world.getFluidState(pos.set(x, y - 1, z));
			if (below.isOf(Fluids.WATER) && below.isStill()) {
				// место, где струя падает в озеро
				double px = x + random.nextDouble();
				double pz = z + random.nextDouble();
				world.addParticleClient(ParticleTypes.CLOUD, px, y + 0.2, pz,
						(random.nextDouble() - 0.5) * 0.04, 0.02 + random.nextDouble() * 0.03, (random.nextDouble() - 0.5) * 0.04);
				world.addParticleClient(ParticleTypes.SPLASH, px, y + 0.4, pz, 0.0, 0.0, 0.0);
				return;
			}
			if (random.nextInt(6) == 0) {
				world.addParticleClient(ParticleTypes.SPLASH, x + random.nextDouble(), y + random.nextDouble(),
						z + random.nextDouble(), 0.0, 0.0, 0.0);
			}
			return;
		}
	}
}
