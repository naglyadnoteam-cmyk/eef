package com.naglyadno.nedra.mixin;

import com.naglyadno.nedra.entity.PrismGaleEntity;
import net.minecraft.block.spawner.MobSpawnerLogic;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Спавнер хрустальных вихрей работает спокойнее ванильного: двое стражей примерно раз в минуту и не
 * больше трёх рядом. Настройка применяется к любому спавнеру с вихрем - и к новым цитаделям, и к уже
 * сгенерированным в старых мирах, и к спавнеру, на который игрок применил яйцо.
 */
@Mixin(MobSpawnerLogic.class)
public abstract class MobSpawnerLogicMixin {

	@Unique
	private static final int GALE_SPAWN_COUNT = 2;
	@Unique
	private static final int GALE_MIN_DELAY = 1100;
	@Unique
	private static final int GALE_MAX_DELAY = 1300;
	@Unique
	private static final int GALE_MAX_NEARBY = 3;

	@Shadow
	private int spawnCount;
	@Shadow
	private int minSpawnDelay;
	@Shadow
	private int maxSpawnDelay;
	@Shadow
	private int maxNearbyEntities;
	@Shadow
	private int spawnDelay;

	@Unique
	private int nedra$recheck;

	@Shadow
	public abstract Entity getRenderedEntity(World world, BlockPos pos);

	@Inject(method = "serverTick", at = @At("HEAD"))
	private void nedra$tunePrismGaleSpawner(ServerWorld world, BlockPos pos, CallbackInfo ci) {
		// какой моб в спавнере, проверяем раз в 10 секунд - это дёшево и ловит смену моба яйцом
		if (nedra$recheck-- > 0) {
			return;
		}
		nedra$recheck = 200;
		if (!(getRenderedEntity(world, pos) instanceof PrismGaleEntity) || spawnCount == GALE_SPAWN_COUNT
				&& minSpawnDelay == GALE_MIN_DELAY && maxSpawnDelay == GALE_MAX_DELAY && maxNearbyEntities == GALE_MAX_NEARBY) {
			return;
		}
		spawnCount = GALE_SPAWN_COUNT;
		minSpawnDelay = GALE_MIN_DELAY;
		maxSpawnDelay = GALE_MAX_DELAY;
		maxNearbyEntities = GALE_MAX_NEARBY;
		spawnDelay = Math.min(spawnDelay, GALE_MAX_DELAY);
	}
}
