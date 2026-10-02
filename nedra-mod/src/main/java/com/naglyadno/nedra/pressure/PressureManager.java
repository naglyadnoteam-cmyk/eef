package com.naglyadno.nedra.pressure;

import com.naglyadno.nedra.NedraMod;
import com.naglyadno.nedra.config.NedraConfig;
import com.naglyadno.nedra.effect.ModEffects;
import com.naglyadno.nedra.item.ModItems;
import com.naglyadno.nedra.network.PressurePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Давление растёт с глубиной (только в обычном мире). Шлем и таблетка снижают его. Последствия:
 * замедление добычи (модификатор атрибута, а не статус-эффект) и, на экстремальной глубине
 * без защиты, периодический урон, который никогда не убивает. Звуки и визуальные эффекты
 * давления проигрывает клиент - их слышит только сам игрок.
 */
public class PressureManager {

	private static final Identifier MINING_MODIFIER_ID = Identifier.of(NedraMod.MOD_ID, "pressure_mining_slowdown");

	private final MinecraftServer server;
	private final NedraConfig config;
	private final Map<UUID, Long> lastDamageTick = new HashMap<>();
	private long tickCounter;

	public PressureManager(MinecraftServer server, NedraConfig config) {
		this.server = server;
		this.config = config;
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

	public void onDisconnect(ServerPlayerEntity player) {
		lastDamageTick.remove(player.getUuid());
	}

	/** Снимок давления для игрока - используется и тиком, и командой /nedra info. */
	public Reading read(ServerPlayerEntity player) {
		boolean active = player.getEntityWorld().getRegistryKey() == World.OVERWORLD;
		if (!active) {
			return new Reading(0, 0, 0, PressureTier.NONE, false);
		}
		double span = Math.max(1, config.surfaceY - config.deepY);
		double depthFraction = clamp((config.surfaceY - player.getY()) / span, 0.0, 1.0);
		double raw = depthFraction * 100.0;
		int protection = Math.min(95, helmetReductionPercent(player.getEquippedStack(EquipmentSlot.HEAD).getItem())
				+ (player.hasStatusEffect(ModEffects.PRESSURE_RESISTANCE) ? config.tabletReductionPercent : 0));
		double effective = clamp(raw * (1.0 - protection / 100.0), 0.0, 100.0);
		return new Reading(effective, raw, protection, PressureTier.fromValue(effective), true);
	}

	private void updatePlayer(ServerPlayerEntity player) {
		Reading reading = read(player);
		boolean exempt = player.isCreative() || player.isSpectator();
		applyMiningSlowdown(player, exempt ? PressureTier.NONE : reading.tier());
		if (!exempt && reading.active()) {
			applyDamage(player, reading.tier());
		}
		ServerPlayNetworking.send(player, new PressurePayload((float) reading.effective(), (float) reading.raw(),
				reading.protection(), reading.tier().level, reading.active()));
	}

	private void applyMiningSlowdown(ServerPlayerEntity player, PressureTier tier) {
		EntityAttributeInstance attribute = player.getAttributeInstance(EntityAttributes.BLOCK_BREAK_SPEED);
		if (attribute == null) {
			return;
		}
		int steps = tier.level - config.miningSlowdownStartTier + 1;
		if (steps <= 0) {
			if (attribute.hasModifier(MINING_MODIFIER_ID)) {
				attribute.removeModifier(MINING_MODIFIER_ID);
			}
			return;
		}
		double value = -Math.min(0.9, steps * config.miningSlowdownPerTier);
		for (EntityAttributeModifier existing : attribute.getModifiers()) {
			if (existing.idMatches(MINING_MODIFIER_ID) && existing.value() == value) {
				return;
			}
		}
		attribute.removeModifier(MINING_MODIFIER_ID);
		attribute.addTemporaryModifier(new EntityAttributeModifier(MINING_MODIFIER_ID, value,
				EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	}

	private void applyDamage(ServerPlayerEntity player, PressureTier tier) {
		if (tier.level < config.damageStartTier) {
			return;
		}
		long now = server.getTicks();
		Long last = lastDamageTick.get(player.getUuid());
		if (last != null && now - last < config.damageIntervalTicks) {
			return;
		}
		float amount = Math.min(config.damageAmount, player.getHealth() - 1.0f);
		if (amount <= 0.05f) {
			return;
		}
		lastDamageTick.put(player.getUuid(), now);
		ServerWorld world = player.getEntityWorld();
		player.damage(world, ModDamageTypes.pressure(world), amount);
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

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	public record Reading(double effective, double raw, int protection, PressureTier tier, boolean active) {
	}
}
