package com.naglyadno.nedra.util;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.function.Predicate;

/**
 * Поиск ближайшего блока в кубе вокруг точки. Вместо перебора каждой позиции сначала спрашиваем
 * у палитры секции чанка 16x16x16, есть ли в ней искомый блок вообще (ChunkSection#hasAny) -
 * подавляющее большинство секций отбрасывается целиком. Незагруженные чанки не трогаются.
 */
public final class BlockScanner {

	private BlockScanner() {
	}

	public static BlockPos findNearest(ServerWorld world, BlockPos center, int radius, Block block) {
		return findNearest(world, center, radius, state -> state.isOf(block));
	}

	public static BlockPos findNearest(ServerWorld world, BlockPos center, int radius, Predicate<BlockState> predicate) {
		int minY = Math.max(world.getBottomY(), center.getY() - radius);
		int maxY = Math.min(world.getTopYInclusive(), center.getY() + radius);
		if (minY > maxY) {
			return null;
		}
		long radiusSq = (long) radius * radius;
		BlockPos best = null;
		long bestDistSq = Long.MAX_VALUE;
		BlockPos.Mutable cursor = new BlockPos.Mutable();

		int minCX = ChunkSectionPos.getSectionCoord(center.getX() - radius);
		int maxCX = ChunkSectionPos.getSectionCoord(center.getX() + radius);
		int minCZ = ChunkSectionPos.getSectionCoord(center.getZ() - radius);
		int maxCZ = ChunkSectionPos.getSectionCoord(center.getZ() + radius);
		int minSY = ChunkSectionPos.getSectionCoord(minY);
		int maxSY = ChunkSectionPos.getSectionCoord(maxY);

		for (int cx = minCX; cx <= maxCX; cx++) {
			for (int cz = minCZ; cz <= maxCZ; cz++) {
				if (!world.getChunkManager().isChunkLoaded(cx, cz)) {
					continue;
				}
				WorldChunk chunk = world.getChunk(cx, cz);
				for (int sy = minSY; sy <= maxSY; sy++) {
					int index = chunk.sectionCoordToIndex(sy);
					if (index < 0 || index >= chunk.getSectionArray().length) {
						continue;
					}
					ChunkSection section = chunk.getSection(index);
					if (section.isEmpty() || !section.hasAny(predicate)) {
						continue;
					}
					int baseX = cx << 4;
					int baseY = sy << 4;
					int baseZ = cz << 4;
					for (int ly = 0; ly < 16; ly++) {
						int y = baseY + ly;
						if (y < minY || y > maxY) {
							continue;
						}
						for (int lz = 0; lz < 16; lz++) {
							for (int lx = 0; lx < 16; lx++) {
								if (!predicate.test(section.getBlockState(lx, ly, lz))) {
									continue;
								}
								cursor.set(baseX + lx, y, baseZ + lz);
								long d = (long) cursor.getSquaredDistance(center);
								if (d <= radiusSq && d < bestDistSq) {
									bestDistSq = d;
									best = cursor.toImmutable();
								}
							}
						}
					}
				}
			}
		}
		return best;
	}
}
