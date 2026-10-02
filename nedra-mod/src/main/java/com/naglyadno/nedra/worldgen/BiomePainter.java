package com.naglyadno.nedra.worldgen;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Перекрашивает биом уже сгенерированных чанков в кастомный тот же приём,
 * которым пользуется ванильная команда /fillbiome (Chunk#populateBiomes),
 * но вызванный из тика сервера, а не во время самой генерации чанка, где
 * полноценный ServerWorld ещё недоступен.
 */
public final class BiomePainter {

	private record Job(BlockBox box, RegistryKey<Biome> biome) {
	}

	private static final Deque<Job> QUEUE = new ArrayDeque<>();
	private static final int MAX_PER_TICK = 2;

	private BiomePainter() {
	}

	public static void queue(BlockBox box, RegistryKey<Biome> biome) {
		synchronized (QUEUE) {
			QUEUE.add(new Job(box, biome));
		}
	}

	public static void tick(ServerWorld world) {
		for (int i = 0; i < MAX_PER_TICK; i++) {
			Job job;
			synchronized (QUEUE) {
				job = QUEUE.poll();
			}
			if (job == null) {
				return;
			}
			if (!paint(world, job)) {
				synchronized (QUEUE) {
					QUEUE.add(job);
				}
				return;
			}
		}
	}

	private static boolean paint(ServerWorld world, Job job) {
		BlockBox box = job.box();
		Biome biome = world.getRegistryManager().getOrThrow(RegistryKeys.BIOME).getOrThrow(job.biome());
		RegistryEntry<Biome> biomeEntry = RegistryEntry.of(biome);

		List<Chunk> chunks = new ArrayList<>();
		int minChunkX = ChunkSectionPos.getSectionCoord(box.getMinX());
		int maxChunkX = ChunkSectionPos.getSectionCoord(box.getMaxX());
		int minChunkZ = ChunkSectionPos.getSectionCoord(box.getMinZ());
		int maxChunkZ = ChunkSectionPos.getSectionCoord(box.getMaxZ());

		for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
			for (int cx = minChunkX; cx <= maxChunkX; cx++) {
				Chunk chunk = world.getChunk(cx, cz, ChunkStatus.FULL, false);
				if (chunk == null) {
					return false;
				}
				chunks.add(chunk);
			}
		}

		for (Chunk chunk : chunks) {
			chunk.populateBiomes((x, y, z, noise) -> {
				int blockX = BiomeCoords.toBlock(x);
				int blockY = BiomeCoords.toBlock(y);
				int blockZ = BiomeCoords.toBlock(z);
				if (box.contains(blockX, blockY, blockZ)) {
					return biomeEntry;
				}
				return chunk.getBiomeForNoiseGen(x, y, z);
			}, world.getChunkManager().getNoiseConfig().getMultiNoiseSampler());
			chunk.markNeedsSaving();
		}

		return true;
	}
}
