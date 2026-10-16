package com.naglyadno.prostor.mixin;

import com.mojang.datafixers.util.Pair;
import com.naglyadno.prostor.worldgen.BiomePlacement;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.biome.source.util.VanillaBiomeParameters;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/** Отдаёт часть ванильных климатических зон биомам мода (см. {@link BiomePlacement}). */
@Mixin(VanillaBiomeParameters.class)
public abstract class VanillaBiomeParametersMixin {

	@Inject(method = "writeBiomeParameters", at = @At("HEAD"), cancellable = true)
	private void prostor$placeModBiomes(Consumer<Pair<MultiNoiseUtil.NoiseHypercube, RegistryKey<Biome>>> parameters,
			MultiNoiseUtil.ParameterRange temperature, MultiNoiseUtil.ParameterRange humidity,
			MultiNoiseUtil.ParameterRange continentalness, MultiNoiseUtil.ParameterRange erosion,
			MultiNoiseUtil.ParameterRange weirdness, float offset, RegistryKey<Biome> biome, CallbackInfo ci) {
		if (BiomePlacement.split(parameters, temperature, humidity, continentalness, erosion, weirdness, offset, biome)) {
			ci.cancel();
		}
	}
}
