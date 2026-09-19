package com.naglyadno.fatecards.game;

import com.naglyadno.fatecards.cards.Card;
import com.naglyadno.fatecards.cards.CardContext;
import com.naglyadno.fatecards.cards.CardRegistry;
import com.naglyadno.fatecards.config.FateCardsConfig;
import com.naglyadno.fatecards.network.FateStatePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Главный управляющий класс Fate Cards: раунды, отложенные задачи, история позиций, выбор карт. */
public class GameManager {

	private final MinecraftServer server;
	private final FateCardsConfig config;
	private final Path configPath;
	private final Random random = new Random();

	private GameState state = GameState.WAITING;
	private long tickCounter;
	private int roundTimerTicks;

	private final Map<UUID, PendingOffer> pendingOffers = new HashMap<>();
	private final Map<UUID, Deque<PosSample>> history = new HashMap<>();
	private final List<ScheduledTask> scheduled = new ArrayList<>();

	private String lastAnnouncement = "";

	public GameManager(MinecraftServer server, FateCardsConfig config, Path configPath) {
		this.server = server;
		this.config = config;
		this.configPath = configPath;
	}

	public FateCardsConfig config() {
		return config;
	}

	public GameState state() {
		return state;
	}

	public MinecraftServer server() {
		return server;
	}

	// ------------------------------------------------------------------
	// Lifecycle
	// ------------------------------------------------------------------

	public Text start() {
		if (state == GameState.RUNNING) {
			return Text.literal("Fate Cards уже идёт.").formatted(Formatting.RED);
		}
		List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
		if (players.size() < config.minPlayers) {
			return Text.literal("Недостаточно игроков онлайн: нужно минимум " + config.minPlayers + ".")
					.formatted(Formatting.RED);
		}
		state = GameState.RUNNING;
		roundTimerTicks = Math.max(20, config.firstRoundDelaySeconds * 20);
		pendingOffers.clear();
		lastAnnouncement = "";
		broadcastAll(Text.literal("Судьба пробудилась! Первый выбор карт через " + config.firstRoundDelaySeconds + "с.")
				.formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
		return Text.literal("Fate Cards запущен!").formatted(Formatting.GREEN);
	}

	public Text stop() {
		if (state == GameState.WAITING) {
			return Text.literal("Fate Cards и так не запущен.").formatted(Formatting.YELLOW);
		}
		state = GameState.WAITING;
		pendingOffers.clear();
		lastAnnouncement = "";
		broadcastAll(Text.literal("Fate Cards остановлен.").formatted(Formatting.YELLOW));
		return Text.literal("Остановлено.").formatted(Formatting.YELLOW);
	}

	// ------------------------------------------------------------------
	// Планировщик отложенных задач
	// ------------------------------------------------------------------

	private record ScheduledTask(long dueTick, Runnable action) {
	}

	public void schedule(int delayTicks, Runnable action) {
		scheduled.add(new ScheduledTask(tickCounter + Math.max(1, delayTicks), action));
	}

	/** Повторяет действие repeats раз с интервалом intervalTicks; индекс повтора передаётся в action. */
	public void scheduleRepeating(int intervalTicks, int repeats, java.util.function.IntConsumer action) {
		scheduleRepeatingInternal(intervalTicks, repeats, 0, action);
	}

	private void scheduleRepeatingInternal(int interval, int repeats, int index, java.util.function.IntConsumer action) {
		if (index >= repeats) {
			return;
		}
		schedule(interval, () -> {
			action.accept(index);
			scheduleRepeatingInternal(interval, repeats, index + 1, action);
		});
	}

	private void runScheduledTasks() {
		if (scheduled.isEmpty()) {
			return;
		}
		List<ScheduledTask> due = new ArrayList<>();
		scheduled.removeIf(task -> {
			if (tickCounter >= task.dueTick()) {
				due.add(task);
				return true;
			}
			return false;
		});
		for (ScheduledTask task : due) {
			try {
				task.action().run();
			} catch (Exception e) {
				com.naglyadno.fatecards.FateCardsMod.LOGGER.error("Ошибка в отложенном эффекте Fate Cards", e);
			}
		}
	}

	// ------------------------------------------------------------------
	// История позиций (для карты "Назад во времени")
	// ------------------------------------------------------------------

	private record PosSample(long tick, ServerWorld world, double x, double y, double z) {
	}

	private void samplePositions() {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			Deque<PosSample> deque = history.computeIfAbsent(player.getUuid(), k -> new ArrayDeque<>());
			deque.addLast(new PosSample(tickCounter, player.getServerWorld(), player.getX(), player.getY(), player.getZ()));
			while (deque.size() > 40) {
				deque.removeFirst();
			}
		}
	}

	/** Возвращает точку игрока примерно secondsAgo секунд назад, либо null если истории ещё нет. */
	public ServerWorld teleportToPast(ServerPlayerEntity player, int secondsAgo) {
		Deque<PosSample> deque = history.get(player.getUuid());
		if (deque == null || deque.isEmpty()) {
			return null;
		}
		long targetTick = tickCounter - secondsAgo * 20L;
		PosSample best = null;
		for (PosSample sample : deque) {
			if (best == null || Math.abs(sample.tick() - targetTick) < Math.abs(best.tick() - targetTick)) {
				best = sample;
			}
		}
		if (best == null) {
			return null;
		}
		player.teleport(best.world(), best.x(), best.y(), best.z(), java.util.Set.of(), player.getYaw(), player.getPitch());
		return best.world();
	}

	// ------------------------------------------------------------------
	// Основной тик
	// ------------------------------------------------------------------

	public void tick() {
		tickCounter++;
		runScheduledTasks();

		if (tickCounter % 100 == 0) {
			samplePositions();
		}

		if (state == GameState.RUNNING) {
			tickPendingOffers();
			roundTimerTicks--;
			if (roundTimerTicks <= 0) {
				startRound();
				roundTimerTicks = Math.max(20, config.roundIntervalSeconds * 20);
			}
		}

		if (tickCounter % 4 == 0) {
			broadcastState();
		}
	}

	private void tickPendingOffers() {
		if (pendingOffers.isEmpty()) {
			return;
		}
		List<UUID> expired = new ArrayList<>();
		for (Map.Entry<UUID, PendingOffer> entry : pendingOffers.entrySet()) {
			if (tickCounter >= entry.getValue().expireTick()) {
				expired.add(entry.getKey());
			}
		}
		for (UUID chooserId : expired) {
			PendingOffer offer = pendingOffers.remove(chooserId);
			if (offer == null) {
				continue;
			}
			Card chosen = random.nextBoolean() ? offer.card1() : offer.card2();
			resolve(chooserId, offer, chosen);
		}
	}

	// ------------------------------------------------------------------
	// Раунды
	// ------------------------------------------------------------------

	private record PendingOffer(UUID targetId, Card card1, Card card2, long expireTick) {
	}

	private void startRound() {
		List<ServerPlayerEntity> players = new ArrayList<>(server.getPlayerManager().getPlayerList());
		if (players.size() < 2) {
			return;
		}
		Collections.shuffle(players, random);
		int size = players.size();
		pendingOffers.clear();

		long expireTick = tickCounter + Math.max(20, config.decisionSeconds * 20);
		for (int i = 0; i < size; i++) {
			ServerPlayerEntity chooser = players.get(i);
			ServerPlayerEntity target = players.get((i + 1) % size);
			Card card1 = CardRegistry.randomCard(random);
			Card card2 = CardRegistry.randomCardExcluding(random, card1);
			pendingOffers.put(chooser.getUuid(), new PendingOffer(target.getUuid(), card1, card2, expireTick));
		}

		broadcastAll(Text.literal("🎴 Судьба взывает! Все выбирают карту для соседа...")
				.formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
	}

	/** Вызывается из сетевого обработчика, когда игрок явно нажал клавишу выбора карты. */
	public void chooseCard(ServerPlayerEntity chooser, String cardId) {
		PendingOffer offer = pendingOffers.get(chooser.getUuid());
		if (offer == null) {
			return;
		}
		Card chosen;
		if (offer.card1().id().equals(cardId)) {
			chosen = offer.card1();
		} else if (offer.card2().id().equals(cardId)) {
			chosen = offer.card2();
		} else {
			return;
		}
		pendingOffers.remove(chooser.getUuid());
		resolve(chooser.getUuid(), offer, chosen);
	}

	private void resolve(UUID chooserId, PendingOffer offer, Card chosen) {
		ServerPlayerEntity chooser = server.getPlayerManager().getPlayer(chooserId);
		ServerPlayerEntity target = server.getPlayerManager().getPlayer(offer.targetId());
		if (chooser == null || target == null) {
			return;
		}
		CardContext ctx = new CardContext(server, this, chooser, target, random);
		try {
			chosen.apply(ctx);
		} catch (Exception e) {
			com.naglyadno.fatecards.FateCardsMod.LOGGER.error("Ошибка применения карты " + chosen.id(), e);
		}
		lastAnnouncement = chooser.getGameProfile().getName() + " наслал(а) на " + target.getGameProfile().getName()
				+ ": " + chosen.name() + "!";
		broadcastAll(Text.literal(lastAnnouncement).formatted(chosen.category().color()));
	}

	// ------------------------------------------------------------------
	// Сеть
	// ------------------------------------------------------------------

	private void broadcastState() {
		int secondsToNext = state == GameState.RUNNING ? Math.max(0, (roundTimerTicks + 19) / 20) : 0;
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			PendingOffer offer = pendingOffers.get(player.getUuid());
			FateStatePayload payload;
			if (offer != null) {
				ServerPlayerEntity target = server.getPlayerManager().getPlayer(offer.targetId());
				String targetName = target != null ? target.getGameProfile().getName() : "?";
				int left = Math.max(0, (int) ((offer.expireTick() - tickCounter + 19) / 20));
				payload = new FateStatePayload(
						state.name(), secondsToNext, true, targetName,
						offer.card1().id(), offer.card1().name(), offer.card1().description(), offer.card1().category().name(),
						offer.card2().id(), offer.card2().name(), offer.card2().description(), offer.card2().category().name(),
						left, lastAnnouncement
				);
			} else {
				payload = new FateStatePayload(
						state.name(), secondsToNext, false, "",
						"", "", "", "",
						"", "", "", "",
						0, lastAnnouncement
				);
			}
			ServerPlayNetworking.send(player, payload);
		}
	}

	private void broadcastAll(Text text) {
		server.getPlayerManager().broadcast(text, false);
	}
}
