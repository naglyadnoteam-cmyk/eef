package com.naglyadno.nedra.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.naglyadno.nedra.sound.ModSounds;
import net.minecraft.block.BlockState;
import net.minecraft.block.ExperienceDroppingBlock;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.intprovider.IntProvider;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Руда, которую почти не видно на глаз - её выдаёт тихий кристаллический звон.
 * Звон проигрывается на клиенте из randomDisplayTick: клиент сам опрашивает блоки вокруг игрока,
 * поэтому руду слышно сквозь камень, а сервер не тратит ни одного тика на тысячи таких блоков.
 */
public class EchoOreBlock extends ExperienceDroppingBlock {

	public static final MapCodec<EchoOreBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			IntProvider.createValidatingCodec(0, 10).fieldOf("experience").forGetter(block -> block.experience),
			createSettingsCodec()
	).apply(instance, EchoOreBlock::new));

	private final IntProvider experience;

	public EchoOreBlock(IntProvider experience, Settings settings) {
		super(experience, settings);
		this.experience = experience;
	}

	@Override
	public MapCodec<EchoOreBlock> getCodec() {
		return CODEC;
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (random.nextInt(3) != 0) {
			return;
		}
		world.playSoundClient(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, ModSounds.ECHO_ORE_CHIME,
				SoundCategory.AMBIENT, 0.45f + random.nextFloat() * 0.25f, 0.85f + random.nextFloat() * 0.3f, true);
	}
}
