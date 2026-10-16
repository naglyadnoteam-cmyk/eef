package com.naglyadno.prostor.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

import java.util.List;

/**
 * Гигантские водопады. Наверху у кромки обрыва - ручей с родниковой водой (вода в кромке получает
 * запланированное обновление и сама срывается вниз, как ванильные родники), внизу - озеро.
 * Меняет только свои столбцы.
 */
public class WaterfallFeature extends Feature<DefaultFeatureConfig> {

	private static final BlockState AIR = Blocks.AIR.getDefaultState();
	private static final BlockState WATER = Blocks.WATER.getDefaultState();
	private static final int FLAGS = Block.NOTIFY_LISTENERS;
	/** Длина ручья наверху. */
	private static final int STREAM = 7;

	public WaterfallFeature(Codec<DefaultFeatureConfig> codec) {
		super(codec);
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
		StructureWorldAccess world = context.getWorld();
		ChunkPos chunk = new ChunkPos(context.getOrigin());
		int bx = chunk.getStartX();
		int bz = chunk.getStartZ();
		Lakes.Terrain terrain = LakeFeature.terrain(world, context.getGenerator());
		List<Waterfalls.Site> sites = Waterfalls.near(terrain, bx, bz, bx + 15, bz + 15);
		if (sites.isEmpty()) {
			return false;
		}
		long seed = world.getSeed();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (Waterfalls.Site site : sites) {
			for (int x = bx; x < bx + 16; x++) {
				for (int z = bz; z < bz + 16; z++) {
					stream(world, site, x, z, pos);
					pool(world, site, seed, x, z, pos);
				}
			}
		}
		return true;
	}

	/** Ручей наверху: русло в 1 блок глубиной до самой кромки, с бортами. */
	private static void stream(StructureWorldAccess world, Waterfalls.Site site, int x, int z, BlockPos.Mutable pos) {
		double s = site.along(x + 0.5, z + 0.5);
		double k = Math.abs(site.across(x + 0.5, z + 0.5));
		double half = site.width() / 2.0;
		if (s < -0.5 || s > STREAM + 1.5 || k > half + 1.5) {
			return;
		}
		int level = site.topY();
		int top = LakeFeature.solidTop(world, x, z, pos);
		boolean inside = k <= half && s <= STREAM + 0.5;
		if (!inside) {
			// борта и задняя стенка русла: не ниже уровня воды, чтобы вода текла только к кромке
			if (top < level) {
				for (int y = top + 1; y <= level; y++) {
					world.setBlockState(pos.set(x, y, z), Blocks.MOSSY_COBBLESTONE.getDefaultState(), FLAGS);
				}
			}
			return;
		}
		if (top < level - 1) {
			return; // уже за кромкой обрыва - там вода падает сама
		}
		for (int y = level + 1; y <= top; y++) {
			world.setBlockState(pos.set(x, y, z), AIR, FLAGS);
		}
		if (world.getBlockState(pos.set(x, level - 1, z)).isAir()) {
			world.setBlockState(pos, Blocks.STONE.getDefaultState(), FLAGS);
		}
		world.setBlockState(pos.set(x, level - 1, z), Blocks.GRAVEL.getDefaultState(), FLAGS);
		world.setBlockState(pos.set(x, level, z), WATER, FLAGS);
		if (s < 1.5) {
			// у кромки вода сразу начинает течь вниз
			world.scheduleFluidTick(pos.toImmutable(), Fluids.WATER, 1);
		}
	}

	/** Озеро под водопадом. */
	private static void pool(StructureWorldAccess world, Waterfalls.Site site, long seed, int x, int z, BlockPos.Mutable pos) {
		double dx = x + 0.5 - site.px();
		double dz = z + 0.5 - site.pz();
		double r = Math.sqrt(dx * dx + dz * dz);
		double radius = site.poolRadius() * (1.0 + 0.15 * Math.sin(Math.atan2(dz, dx) * 3 + site.cellX()));
		if (r > radius + 3) {
			return;
		}
		int level = site.poolY();
		int top = LakeFeature.solidTop(world, x, z, pos);
		if (r <= radius) {
			if (top > level + 3) {
				return; // подножие скалы: вода упирается в неё
			}
			int depth = r < radius * 0.5 ? 3 : r < radius * 0.8 ? 2 : 1;
			int floor = level - depth;
			for (int y = level + 1; y <= top; y++) {
				world.setBlockState(pos.set(x, y, z), AIR, FLAGS);
			}
			for (int y = top + 1; y < floor; y++) {
				world.setBlockState(pos.set(x, y, z), Blocks.DIRT.getDefaultState(), FLAGS);
			}
			for (int y = floor + 1; y <= level; y++) {
				world.setBlockState(pos.set(x, y, z), WATER, FLAGS);
			}
			double h = Lakes.hash(seed, x, z, 51);
			world.setBlockState(pos.set(x, floor, z), h < 0.5 ? Blocks.GRAVEL.getDefaultState()
					: h < 0.8 ? Blocks.STONE.getDefaultState() : Blocks.CLAY.getDefaultState(), FLAGS);
			for (int y = floor - 2; y < floor; y++) {
				if (world.getBlockState(pos.set(x, y, z)).isAir() || !world.getFluidState(pos).isEmpty()) {
					world.setBlockState(pos, Blocks.STONE.getDefaultState(), FLAGS);
				}
			}
			return;
		}
		// берег озера: вал не ниже воды и мшистые камни
		if (top < level) {
			for (int y = top + 1; y <= level; y++) {
				world.setBlockState(pos.set(x, y, z), Blocks.DIRT.getDefaultState(), FLAGS);
			}
			world.setBlockState(pos.set(x, level, z), Blocks.GRASS_BLOCK.getDefaultState(), FLAGS);
			top = level;
		}
		BlockState ground = world.getBlockState(pos.set(x, top, z));
		double h = Lakes.hash(seed, x, z, 52);
		if (ground.isOf(Blocks.GRASS_BLOCK) && h < 0.35) {
			world.setBlockState(pos, h < 0.2 ? Blocks.MOSS_BLOCK.getDefaultState() : Blocks.MOSSY_COBBLESTONE.getDefaultState(), FLAGS);
		}
		for (int y = level - 3; y <= level; y++) {
			BlockState state = world.getBlockState(pos.set(x, y, z));
			if (state.isAir() || !state.getFluidState().isEmpty()) {
				world.setBlockState(pos, Blocks.STONE.getDefaultState(), FLAGS);
			}
		}
	}
}
