package com.naglyadno.nedra.pressure;

import com.naglyadno.nedra.NedraMod;
import com.naglyadno.nedra.config.NedraConfig;
import com.naglyadno.nedra.effect.ModEffects;
import com.naglyadno.nedra.item.ModItems;
import com.naglyadno.nedra.network.PressurePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.nio.file.Path;

/** Раз в config.pressureUpdateIntervalTicks считает давление каждого игрока по глубине и рассылает клиентам. */
public class PressureManager {

	private final MinecraftServer server;
	private final NedraConfig config;
	private final Path configPath;
	private long tickCounter;

	public PressureManager(MinecraftServer server, NedraConfig config, Path configPath) {
		this.server = server;
		this.config = config;
		this.configPath = configPath;
	}

	public NedraConfig config() {
		return config;
	}

	public void tick() {
		tickCounter++;
		if (tickCounter % config.pressureUpdateIntervalTicks != 0) {
			return;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			updatePlayer(player);
		}
	}

	private void updatePlayer(ServerPlayerEntity player) {
		int y = player.getBlockY();
		double span = Math.max(1, config.surfaceY - config.deepY);
		double depthFraction = clamp((config.surfaceY - y) / span, 0.0, 1.0);
		double raw = depthFraction * 100.0;

		int helmetReduction = helmetReductionPercent(player.getEquippedStack(EquipmentSlot.HEAD).getItem());
		int tabletReduction = player.hasStatusEffect(ModEffects.PRESSURE_RESISTANCE) ? config.tabletReductionPercent : 0;
		int totalReduction = Math.min(95, helmetReduction + tabletReduction);

		double effective = clamp(raw * (1.0 - totalReduction / 100.0), 0.0, 100.0);
		PressureTier tier = PressureTier.fromValue(effective);

		applyGameplayEffects(player, tier);

		ServerPlayNetworking.send(player, new PressurePayload(effective, tier.level));
	}

	private int helmetReductionPercent(Item helmet) {
		if (helmet == ModItems.HELMET_DEEPSUIT) {
			return config.helmetDeepsuitReductionPercent;
		}
		if (helmet == ModItems.HELMET_REINFORCED) {
			return config.helmetReinforcedReductionPercent;
		}
		if (helmet == ModItems.HELMET_LIGHT) {
			return config.helmetLightReductionPercent;
		}
		return 0;
	}

	private void applyGameplayEffects(ServerPlayerEntity player, PressureTier tier) {
		if (tier.level >= config.miningFatigueStartTier) {
			int amplifier = Math.min(3, tier.level - config.miningFatigueStartTier);
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE,
					config.pressureUpdateIntervalTicks * 2 + 5, amplifier, true, false));
		}
		if (tier.level >= config.damageStartTier && tickCounter % config.damageIntervalTicks == 0) {
			float newHealth = Math.max(1.0f, player.getHealth() - config.damageAmount);
			player.setHealth(newHealth);
			player.getEntityWorld().playSound(null, player.getBlockPos(),
					net.minecraft.sound.SoundEvents.ENTITY_WARDEN_HEARTBEAT, net.minecraft.sound.SoundCategory.HOSTILE,
					1.0f, 0.6f);
		}
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
