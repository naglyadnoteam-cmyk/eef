package com.naglyadno.speedhunt.game;

import com.naglyadno.speedhunt.config.SpeedHuntConfig;
import com.naglyadno.speedhunt.network.GameStatePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Основной управляющий класс матча Speedhunt: состояние, роли, тайминги, победа/поражение. */
public class GameManager {

	private final MinecraftServer server;
	private final SpeedHuntConfig config;
	private final Random random = new Random();

	private GameState state = GameState.WAITING;
	private final Map<UUID, Role> roles = new HashMap<>();
	private UUID speedrunnerId;

	private int ticksRemaining;
	private int elapsedTicks;
	private int broadcastTickCounter;

	private int trackerCooldownTicks;
	private int trackerActiveTicksLeft;

	private String lastResultMessage = "";

	public GameManager(MinecraftServer server, SpeedHuntConfig config) {
		this.server = server;
		this.config = config;
	}

	public SpeedHuntConfig config() {
		return config;
	}

	public GameState state() {
		return state;
	}

	public Role getRole(UUID id) {
		return roles.getOrDefault(id, Role.NONE);
	}

	// ------------------------------------------------------------------
	// Lifecycle
	// ------------------------------------------------------------------

	public Text start() {
		if (state != GameState.WAITING) {
			return Text.literal("Матч уже идёт. Сначала выполните /speedhunt stop.").formatted(Formatting.RED);
		}
		List<ServerPlayerEntity> players = new ArrayList<>(server.getPlayerManager().getPlayerList());
		if (players.size() < config.minPlayers) {
			return Text.literal("Недостаточно игроков онлайн: нужно минимум " + config.minPlayers + ".")
					.formatted(Formatting.RED);
		}

		roles.clear();
		Collections.shuffle(players, random);
		ServerPlayerEntity speedrunner = players.get(0);
		speedrunnerId = speedrunner.getUuid();
		roles.put(speedrunner.getUuid(), Role.SPEEDRUNNER);
		for (int i = 1; i < players.size(); i++) {
			roles.put(players.get(i).getUuid(), Role.HUNTER);
		}

		applyTeams();
		giveKits();

		for (ServerPlayerEntity player : players) {
			player.sendMessage(Text.literal("Роли распределены... приготовьтесь!").formatted(Formatting.GOLD), false);
		}

		state = GameState.ASSIGNING;
		ticksRemaining = Math.max(1, config.roleRevealDelaySeconds * 20);
		elapsedTicks = 0;
		lastResultMessage = "";

		return Text.literal("Матч запущен! Игроков: " + players.size()).formatted(Formatting.GREEN);
	}

	public Text stop() {
		if (state == GameState.WAITING) {
			return Text.literal("Матч и так не запущен.").formatted(Formatting.YELLOW);
		}
		resetToWaiting();
		return Text.literal("Матч остановлен.").formatted(Formatting.YELLOW);
	}

	// ------------------------------------------------------------------
	// Tick loop
	// ------------------------------------------------------------------

	public void tick() {
		switch (state) {
			case WAITING -> {
			}
			case ASSIGNING -> {
				ticksRemaining--;
				if (ticksRemaining <= 0) {
					revealRoles();
					state = GameState.GRACE;
					ticksRemaining = Math.max(1, config.graceProtectionSeconds * 20);
				}
			}
			case GRACE -> {
				ticksRemaining--;
				if (ticksRemaining <= 0) {
					beginRunning();
				}
			}
			case RUNNING -> {
				elapsedTicks++;
				tickTracker();
			}
			case ENDED -> {
				ticksRemaining--;
				if (ticksRemaining <= 0) {
					resetToWaiting();
				}
			}
		}

		broadcastTickCounter++;
		if (broadcastTickCounter % 4 == 0) {
			broadcastState();
		}
	}

	private void revealRoles() {
		for (Map.Entry<UUID, Role> entry : roles.entrySet()) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
			if (player == null) {
				continue;
			}
			Role role = entry.getValue();
			if (role == Role.SPEEDRUNNER) {
				showTitle(player, "ВЫ — СПИДРАННЕР", "Пройдите игру и не умрите!", Formatting.GREEN);
			} else {
				showTitle(player, "ВЫ — ОХОТНИК", "Найдите и остановите спидраннера!", Formatting.RED);
			}
		}
	}

	private void beginRunning() {
		state = GameState.RUNNING;
		elapsedTicks = 0;
		trackerCooldownTicks = Math.max(1, config.trackerIntervalSeconds * 20);
		trackerActiveTicksLeft = 0;
		broadcastAll(Text.literal("Охотники освобождены! Погоня началась!").formatted(Formatting.RED, Formatting.BOLD));
	}

	private void resetToWaiting() {
		state = GameState.WAITING;
		roles.clear();
		speedrunnerId = null;
		lastResultMessage = "";
		broadcastAll(Text.literal("Лобби сброшено. Наберите /speedhunt start, чтобы начать заново.")
				.formatted(Formatting.AQUA));
		broadcastState();
	}

	private void endGame(Role winnerRole, String message) {
		state = GameState.ENDED;
		ticksRemaining = Math.max(1, config.autoRestartDelaySeconds * 20);
		lastResultMessage = message;
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (winnerRole == Role.SPEEDRUNNER) {
				showTitle(player, "ПОБЕДА СПИДРАННЕРА", message, Formatting.GREEN);
			} else {
				showTitle(player, "ПОБЕДА ОХОТНИКОВ", message, Formatting.RED);
			}
		}
		broadcastAll(Text.literal(message).formatted(Formatting.GOLD, Formatting.BOLD));
	}

	// ------------------------------------------------------------------
	// Combat / win conditions
	// ------------------------------------------------------------------

	/** @return false чтобы отменить урон. */
	public boolean allowDamage(LivingEntity victim, DamageSource source) {
		if (!(victim instanceof ServerPlayerEntity victimPlayer)) {
			return true;
		}
		if (getRole(victimPlayer.getUuid()) != Role.SPEEDRUNNER) {
			return true;
		}
		if (state != GameState.GRACE && state != GameState.ASSIGNING) {
			return true;
		}
		Entity attacker = source.getAttacker();
		if (attacker instanceof ServerPlayerEntity attackerPlayer && getRole(attackerPlayer.getUuid()) == Role.HUNTER) {
			attackerPlayer.sendMessage(
					Text.literal("Ещё рано атаковать! Подождите окончания отсчёта неприкосновенности.")
							.formatted(Formatting.YELLOW),
					true
			);
			return false;
		}
		return true;
	}

	public void onDeath(LivingEntity entity, DamageSource source) {
		if (state != GameState.RUNNING && state != GameState.GRACE) {
			return;
		}
		if (entity instanceof ServerPlayerEntity player && getRole(player.getUuid()) == Role.SPEEDRUNNER) {
			endGame(Role.HUNTER, "Спидраннер погиб — победили охотники!");
			return;
		}
		if (entity instanceof EnderDragonEntity && state == GameState.RUNNING) {
			endGame(Role.SPEEDRUNNER, "Спидраннер повалил Дракона Края — игра пройдена!");
		}
	}

	public void onPlayerDisconnect(ServerPlayerEntity player) {
		if ((state == GameState.RUNNING || state == GameState.GRACE)
				&& getRole(player.getUuid()) == Role.SPEEDRUNNER) {
			endGame(Role.HUNTER, "Спидраннер вышел с сервера — победили охотники!");
		}
	}

	public void onRespawn(ServerPlayerEntity player) {
		if (!config.teleportHunterOnRespawn || state != GameState.RUNNING) {
			return;
		}
		if (getRole(player.getUuid()) != Role.HUNTER) {
			return;
		}
		ServerPlayerEntity speedrunner = speedrunnerId != null ? server.getPlayerManager().getPlayer(speedrunnerId) : null;
		if (speedrunner == null || player.getServerWorld() != speedrunner.getServerWorld()) {
			return;
		}
		player.teleport(speedrunner.getServerWorld(), speedrunner.getX(), speedrunner.getY(), speedrunner.getZ(),
				Set.of(), speedrunner.getYaw(), speedrunner.getPitch());
	}

	// ------------------------------------------------------------------
	// Tracker (бонусная механика в духе компаса)
	// ------------------------------------------------------------------

	private void tickTracker() {
		if (!config.trackerEnabled) {
			return;
		}
		if (trackerActiveTicksLeft > 0) {
			trackerActiveTicksLeft--;
			return;
		}
		trackerCooldownTicks--;
		if (trackerCooldownTicks <= 0) {
			trackerActiveTicksLeft = Math.max(1, config.trackerDurationSeconds * 20);
			trackerCooldownTicks = Math.max(1, config.trackerIntervalSeconds * 20);
			ServerPlayerEntity speedrunner = speedrunnerId != null ? server.getPlayerManager().getPlayer(speedrunnerId) : null;
			if (speedrunner != null) {
				speedrunner.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, trackerActiveTicksLeft, 0));
				broadcastAll(Text.literal("Местоположение спидраннера ненадолго раскрыто охотникам!")
						.formatted(Formatting.LIGHT_PURPLE));
			}
		}
	}

	// ------------------------------------------------------------------
	// Networking
	// ------------------------------------------------------------------

	private void broadcastState() {
		ServerPlayerEntity speedrunner = speedrunnerId != null ? server.getPlayerManager().getPlayer(speedrunnerId) : null;
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			Role role = getRole(player.getUuid());
			boolean tracker = false;
			float yaw = 0f;
			float dist = 0f;
			if (role == Role.HUNTER && trackerActiveTicksLeft > 0 && speedrunner != null && speedrunner != player) {
				tracker = true;
				double dx = speedrunner.getX() - player.getX();
				double dz = speedrunner.getZ() - player.getZ();
				dist = (float) Math.sqrt(dx * dx + dz * dz);
				double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
				yaw = MathHelper.wrapDegrees((float) (targetYaw - player.getYaw()));
			}

			int countdown = switch (state) {
				case ASSIGNING, GRACE, ENDED -> (ticksRemaining + 19) / 20;
				default -> 0;
			};

			GameStatePayload payload = new GameStatePayload(
					role.name(),
					state.name(),
					countdown,
					elapsedTicks / 20,
					tracker,
					yaw,
					dist,
					lastResultMessage
			);
			ServerPlayNetworking.send(player, payload);
		}
	}

	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------

	private void applyTeams() {
		Scoreboard scoreboard = server.getScoreboard();
		Team runnerTeam = getOrCreateTeam(scoreboard, "shRunner", Formatting.GREEN);
		Team hunterTeam = getOrCreateTeam(scoreboard, "shHunter", Formatting.RED);
		for (Map.Entry<UUID, Role> entry : roles.entrySet()) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
			if (player == null) {
				continue;
			}
			Team team = entry.getValue() == Role.SPEEDRUNNER ? runnerTeam : hunterTeam;
			scoreboard.addScoreHolderToTeam(player.getNameForScoreboard(), team);
		}
	}

	private Team getOrCreateTeam(Scoreboard scoreboard, String name, Formatting color) {
		Team team = scoreboard.getTeam(name);
		if (team == null) {
			team = scoreboard.addTeam(name);
			team.setColor(color);
		}
		return team;
	}

	private void giveKits() {
		for (Map.Entry<UUID, Role> entry : roles.entrySet()) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
			if (player == null) {
				continue;
			}
			if (entry.getValue() == Role.HUNTER && config.hunterBonusEnabled) {
				giveItem(player, Items.GOLDEN_APPLE, config.hunterBonusGoldenApples);
				giveItem(player, Items.IRON_INGOT, config.hunterBonusIronIngots);
				giveItem(player, Items.ARROW, config.hunterBonusArrows);
			} else if (entry.getValue() == Role.SPEEDRUNNER && config.speedrunnerBonusEnabled) {
				giveItem(player, Items.GOLDEN_APPLE, config.speedrunnerBonusGoldenApples);
				giveItem(player, Items.ENDER_PEARL, config.speedrunnerBonusEnderPearls);
			}
		}
	}

	private void giveItem(ServerPlayerEntity player, Item item, int count) {
		if (count <= 0) {
			return;
		}
		ItemStack stack = new ItemStack(item, count);
		if (!player.getInventory().insertStack(stack)) {
			player.dropItem(stack, false);
		}
	}

	private void showTitle(ServerPlayerEntity player, String title, String subtitle, Formatting color) {
		player.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 60, 10));
		player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(subtitle).formatted(Formatting.WHITE)));
		player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(title).formatted(color, Formatting.BOLD)));
		player.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.MASTER, 1f, 1f);
	}

	private void broadcastAll(Text text) {
		server.getPlayerManager().broadcast(text, false);
	}
}
