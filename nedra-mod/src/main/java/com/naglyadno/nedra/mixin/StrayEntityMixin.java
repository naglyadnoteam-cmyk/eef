package com.naglyadno.nedra.mixin;

import com.naglyadno.nedra.worldgen.deep.FrozenCaverns;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.StrayEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ServerWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Зимогоры в ванильной игре появляются только под открытым небом. В Замёрзших пещерах неба нет, поэтому
 * там им достаточно темноты - как обычным скелетам.
 */
@Mixin(StrayEntity.class)
public abstract class StrayEntityMixin {

	@Inject(method = "canSpawn", at = @At("RETURN"), cancellable = true)
	private static void nedra$spawnInFrozenCaverns(EntityType<StrayEntity> type, ServerWorldAccess world, SpawnReason spawnReason,
			BlockPos pos, Random random, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && world.getBiome(pos).matchesKey(FrozenCaverns.BIOME)
				&& HostileEntity.canSpawnInDark(type, world, spawnReason, pos, random)) {
			cir.setReturnValue(true);
		}
	}
}
