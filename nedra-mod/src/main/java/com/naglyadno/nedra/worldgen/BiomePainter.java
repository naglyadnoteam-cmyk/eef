package com.naglyadno.nedra.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.naglyadno.nedra.NedraMod;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Перекраска биома под каверны и поселения. Генерация фич идёт на рабочих потоках, где нет
 * полноценного ServerWorld, поэтому фичи только кладут заявку в потокобезопасную очередь.
 * Серверный поток раскладывает заявки по чанкам и хранит их в сохранении мира (PersistentState),
 * а применяет тем же способом, что и ванильная команда /fillbiome (Chunk#populateBiomes) -
 * как только нужный чанк загружен, - и сразу рассылает клиентам обновлённые биомы.
 */
public final class BiomePainter extends PersistentState {

	private record Job(long chunk, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Identifier biome) {
		static final Codec<Job> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.LONG.fieldOf("chunk").forGetter(Job::chunk),
				Codec.INT.fieldOf("min_x").forGetter(Job::minX),
				Codec.INT.fieldOf("min_y").forGetter(Job::minY),
				Codec.INT.fieldOf("min_z").forGetter(Job::minZ),
				Codec.INT.fieldOf("max_x").forGetter(Job::maxX),
				Codec.INT.fieldOf("max_y").forGetter(Job::maxY),
				Codec.INT.fieldOf("max_z").forGetter(Job::maxZ),
				Identifier.CODEC.fieldOf("biome").forGetter(Job::biome)
		).apply(instance, Job::new));

		boolean contains(int x, int y, int z) {
			return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
		}
	}

	private static final Codec<BiomePainter> CODEC = Job.CODEC.listOf().optionalFieldOf("jobs", List.of()).codec()
			.xmap(BiomePainter::new, BiomePainter::allJobs);

	private static final PersistentStateType<BiomePainter> TYPE =
			new PersistentStateType<>(NedraMod.MOD_ID + "_biome_jobs", BiomePainter::new, CODEC, null);

	/** Заявки от фич с рабочих потоков генерации. */
	private static final ConcurrentLinkedQueue<Job> INCOMING = new ConcurrentLinkedQueue<>();

	private final Map<Long, List<Job>> pending = new HashMap<>();

	private BiomePainter() {
	}

	private BiomePainter(List<Job> jobs) {
		for (Job job : jobs) {
			pending.computeIfAbsent(job.chunk(), k -> new ArrayList<>()).add(job);
		}
	}

	private List<Job> allJobs() {
		List<Job> all = new ArrayList<>();
		pending.values().forEach(all::addAll);
		return all;
	}

	/** Вызывается из Feature#generate (любой поток). */
	public static void queue(BlockBox box, RegistryKey<Biome> biome) {
		int minCX = ChunkSectionPos.getSectionCoord(box.getMinX());
		int maxCX = ChunkSectionPos.getSectionCoord(box.getMaxX());
		int minCZ = ChunkSectionPos.getSectionCoord(box.getMinZ());
		int maxCZ = ChunkSectionPos.getSectionCoord(box.getMaxZ());
		for (int cx = minCX; cx <= maxCX; cx++) {
			for (int cz = minCZ; cz <= maxCZ; cz++) {
				int x0 = Math.max(box.getMinX(), cx << 4);
				int x1 = Math.min(box.getMaxX(), (cx << 4) + 15);
				int z0 = Math.max(box.getMinZ(), cz << 4);
				int z1 = Math.min(box.getMaxZ(), (cz << 4) + 15);
				INCOMING.add(new Job(ChunkPos.toLong(cx, cz), x0, box.getMinY(), z0, x1, box.getMaxY(), z1, biome.getValue()));
			}
		}
	}

	private static BiomePainter get(ServerWorld world) {
		return world.getPersistentStateManager().getOrCreate(TYPE);
	}

	/** Серверный тик: принять новые заявки и докрасить чанки, которые уже загружены. */
	public static void tick(ServerWorld world) {
		BiomePainter state = get(world);
		Job job;
		while ((job = INCOMING.poll()) != null) {
			state.pending.computeIfAbsent(job.chunk(), k -> new ArrayList<>()).add(job);
			state.markDirty();
		}
		if (state.pending.isEmpty()) {
			return;
		}
		int budget = 4;
		Iterator<Map.Entry<Long, List<Job>>> it = state.pending.entrySet().iterator();
		while (it.hasNext() && budget > 0) {
			Map.Entry<Long, List<Job>> entry = it.next();
			int cx = ChunkPos.getPackedX(entry.getKey());
			int cz = ChunkPos.getPackedZ(entry.getKey());
			if (!world.getChunkManager().isChunkLoaded(cx, cz)) {
				continue;
			}
			paint(world, world.getChunk(cx, cz), entry.getValue());
			it.remove();
			state.markDirty();
			budget--;
		}
	}

	/** Чанк только что загрузился (или сгенерировался) - докрашиваем его сразу. */
	public static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
		BiomePainter state = get(world);
		List<Job> jobs = state.pending.remove(chunk.getPos().toLong());
		if (jobs != null) {
			paint(world, chunk, jobs);
			state.markDirty();
		}
	}

	private static void paint(ServerWorld world, WorldChunk chunk, List<Job> jobs) {
		var biomes = world.getRegistryManager().getOrThrow(RegistryKeys.BIOME);
		Map<Identifier, RegistryEntry<Biome>> resolved = new HashMap<>();
		for (Job job : jobs) {
			Optional<RegistryEntry.Reference<Biome>> entry = biomes.getEntry(job.biome());
			if (entry.isEmpty()) {
				NedraMod.LOGGER.warn("Неизвестный биом {} в заявке на перекраску - пропускаю", job.biome());
				continue;
			}
			resolved.put(job.biome(), entry.get());
		}
		if (resolved.isEmpty()) {
			return;
		}
		chunk.populateBiomes((x, y, z, noise) -> {
			int bx = BiomeCoords.toBlock(x);
			int by = BiomeCoords.toBlock(y);
			int bz = BiomeCoords.toBlock(z);
			for (Job job : jobs) {
				RegistryEntry<Biome> biome = resolved.get(job.biome());
				if (biome != null && job.contains(bx, by, bz)) {
					return biome;
				}
			}
			return chunk.getBiomeForNoiseGen(x, y, z);
		}, world.getChunkManager().getNoiseConfig().getMultiNoiseSampler());
		chunk.markNeedsSaving();
		world.getChunkManager().chunkLoadingManager.sendChunkBiomePackets(List.<Chunk>of(chunk));
	}
}
