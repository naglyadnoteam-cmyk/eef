package com.naglyadno.nedra.client;

import com.naglyadno.nedra.NedraMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.joml.Vector3f;

/**
 * Холодный свет Замёрзших пещер. В ванильной игре свет от блоков всегда тёплый (это зашито в шейдер карты
 * освещения), поэтому лёд и снег под кристаллами выглядели бы бежевыми. Внутри пещеры клиент плавно
 * (около секунды) переводит свет блоков в голубой: через неиспользуемый в Верхнем мире цвет окружения
 * передаётся сила эффекта, а шейдер mod'а (assets/minecraft/shaders/core/lightmap.fsh) смешивает тёплую
 * формулу с холодной. Вне пещеры значения ровно ванильные.
 */
public final class ClientFrostLight {

	private static final RegistryKey<Biome> FROZEN = RegistryKey.of(RegistryKeys.BIOME, Identifier.of(NedraMod.MOD_ID, "frozen_caverns"));
	private static final float STEP = 0.05F;

	private static float strength;

	private ClientFrostLight() {
	}

	public static void tick(MinecraftClient client) {
		float target = 0.0F;
		if (client.world != null && client.player != null && client.world.getRegistryKey() == World.OVERWORLD
				&& client.world.getBiome(client.gameRenderer.getCamera().getBlockPos()).matchesKey(FROZEN)) {
			target = 1.0F;
		}
		strength = target > strength ? Math.min(target, strength + STEP) : Math.max(target, strength - STEP);
	}

	public static void reset() {
		strength = 0.0F;
	}

	/** Цвет окружения для карты освещения: (1, 1, 1) вне пещеры, красная составляющая падает до 0.6 внутри. */
	public static Vector3f ambientColor(Vector3f vanilla) {
		if (strength <= 0.0F) {
			return vanilla;
		}
		return new Vector3f(1.0F - 0.4F * strength, vanilla.y(), vanilla.z());
	}
}
