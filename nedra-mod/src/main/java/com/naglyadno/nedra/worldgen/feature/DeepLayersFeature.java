package com.naglyadno.nedra.worldgen.feature;

import com.mojang.serialization.Codec;
import com.naglyadno.nedra.NedraMod;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;
import com.naglyadno.nedra.worldgen.deep.DeepTerrain;
import com.naglyadno.nedra.worldgen.deep.Settlements;

import java.util.List;
import java.util.Optional;

/**
 * Последний шаг генерации чанка для новых глубин (ниже ванильного дна Y -64). Работает только со
 * своим чанком, поэтому безопасен на шаге фич.
 *
 * <ul>
 *     <li>Осушает пещеры: ванильный акифер заливает лавой любую полость ниже Y -54, поэтому без этого
 *     глубинные пещеры были бы сплошными лавовыми туннелями. Лава остаётся только у самого дна.</li>
 *     <li>Расставляет биомы глубин по ярусам {@link DeepTerrain} (а залы поселений - Эхо-пустоты); там, где
 *     биом яруса не выражен, продолжается ванильный.</li>
 * </ul>
 * Биомы ставятся после всех остальных фич, поэтому руды и пещеры генерируются как обычно.
 */
public class DeepLayersFeature extends Feature<DefaultFeatureConfig> {

	/** Всё, что ниже, - новые глубины мода. */
	public static final int DEEP_TOP = -64;
	/** Ниже этой высоты лава остаётся: у дна мира, как и в ванильной игре, лежат лавовые озёра. */
	private static final int KEEP_LAVA_BELOW = -338;

	public DeepLayersFeature(Codec<DefaultFeatureConfig> codec) {
		super(codec);
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
		StructureWorldAccess world = context.getWorld();
		Chunk chunk = world.getChunk(context.getOrigin());
		if (chunk.getBottomY() >= DEEP_TOP) {
			return false;
		}
		drainLava(chunk);
		paintBiomes(world, chunk);
		return true;
	}

	private static void drainLava(Chunk chunk) {
		BlockState air = Blocks.CAVE_AIR.getDefaultState();
		ChunkSection[] sections = chunk.getSectionArray();
		for (int i = 0; i < sections.length; i++) {
			int baseY = chunk.sectionIndexToCoord(i) << 4;
			if (baseY >= DEEP_TOP) {
				break;
			}
			ChunkSection section = sections[i];
			if (section.isEmpty() || !section.hasAny(state -> state.isOf(Blocks.LAVA))) {
				continue;
			}
			for (int ly = 0; ly < 16; ly++) {
				if (baseY + ly < KEEP_LAVA_BELOW) {
					continue;
				}
				for (int lz = 0; lz < 16; lz++) {
					for (int lx = 0; lx < 16; lx++) {
						if (section.getBlockState(lx, ly, lz).isOf(Blocks.LAVA)) {
							section.setBlockState(lx, ly, lz, air);
						}
					}
				}
			}
		}
	}

	private static void paintBiomes(StructureWorldAccess world, Chunk chunk) {
		Registry<Biome> registry = world.getRegistryManager().getOrThrow(RegistryKeys.BIOME);
		Optional<RegistryEntry.Reference<Biome>> echo = registry.getEntry(Identifier.of(NedraMod.MOD_ID, "echo_hollows"));
		Optional<RegistryEntry.Reference<Biome>> magnetic = registry.getEntry(Identifier.of(NedraMod.MOD_ID, "magnetic_caverns"));
		Optional<RegistryEntry.Reference<Biome>> crystal = registry.getEntry(Identifier.of(NedraMod.MOD_ID, "crystal_depths"));
		Optional<RegistryEntry.Reference<Biome>> overgrown = registry.getEntry(Identifier.of(NedraMod.MOD_ID, "overgrown_depths"));
		Optional<RegistryEntry.Reference<Biome>> scarlet = registry.getEntry(Identifier.of(NedraMod.MOD_ID, "scarlet_grottoes"));
		Optional<RegistryEntry.Reference<Biome>> lush = registry.getEntry(Identifier.ofVanilla("lush_caves"));
		if (echo.isEmpty() || magnetic.isEmpty() || crystal.isEmpty() || overgrown.isEmpty() || scarlet.isEmpty() || lush.isEmpty()) {
			return;
		}
		long seed = world.getSeed();
		DeepTerrain terrain = DeepTerrain.of(seed);
		int bx = chunk.getPos().getStartX();
		int bz = chunk.getPos().getStartZ();
		List<Settlements.Site> sites = Settlements.near(seed, bx, bz, bx + 15, bz + 15, 12);
		chunk.populateBiomes((qx, qy, qz, noise) -> {
			RegistryEntry<Biome> current = chunk.getBiomeForNoiseGen(qx, qy, qz);
			int y = BiomeCoords.toBlock(qy);
			if (y >= DEEP_TOP) {
				return current;
			}
			int x = BiomeCoords.toBlock(qx) + 2;
			int z = BiomeCoords.toBlock(qz) + 2;
			for (Settlements.Site site : sites) {
				if (site.distance(x, z) < site.radius() + 12 && y > site.floorY() - 8 && y < site.floorY() + 26) {
					return echo.get();
				}
			}
			DeepTerrain.Layer layer = terrain.dominant(terrain.column(x, z), y);
			// пышные карманы - настоящий ванильный биом пышных пещер (аксолотли, музыка, туман)
			if (layer != DeepTerrain.Layer.SCARLET && terrain.lush(x, y, z)) {
				return lush.get();
			}
			return switch (layer) {
				case ECHO -> echo.get();
				case MAGNETIC -> magnetic.get();
				case CRYSTAL -> crystal.get();
				case JUNGLE -> overgrown.get();
				case SCARLET -> scarlet.get();
				case NONE -> current;
			};
		}, world.toServerWorld().getChunkManager().getNoiseConfig().getMultiNoiseSampler());
	}
}
