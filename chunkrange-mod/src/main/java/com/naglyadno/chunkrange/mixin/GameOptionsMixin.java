package com.naglyadno.chunkrange.mixin;

import com.naglyadno.chunkrange.ChunkRangeClient;
import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * В конструкторе GameOptions слайдеры "Прогрузка чанков" и "Дистанция симуляции" создаются как
 * new SimpleOption.ValidatingIntSliderCallbacks(min, 32 (или 16 на слабой памяти), false).
 * Подменяем второй аргумент (максимум) на расширенный, не трогая остальную логику слайдера.
 */
@Mixin(GameOptions.class)
public class GameOptionsMixin {

	@ModifyArg(
			method = "<init>(Lnet/minecraft/client/MinecraftClient;Ljava/io/File;)V",
			at = @At(
					value = "NEW",
					target = "Lnet/minecraft/client/option/SimpleOption$ValidatingIntSliderCallbacks;"
			),
			index = 1
	)
	private int chunkrange$extendSliderMax(int originalMax) {
		return ChunkRangeClient.MAX_DISTANCE;
	}
}
