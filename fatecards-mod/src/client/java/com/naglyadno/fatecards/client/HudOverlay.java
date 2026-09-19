package com.naglyadno.fatecards.client;

import com.naglyadno.fatecards.network.FateStatePayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Окошко статуса в правом нижнем углу + баннер выбора карты вверху экрана. */
public final class HudOverlay {

	private static final int PADDING = 4;
	private static final int MARGIN = 8;

	private static String lastShownAnnouncement = "";
	private static long announcementShownAtMs;

	private HudOverlay() {
	}

	public static void render(DrawContext context) {
		ClientSettings settings = ClientSettings.get();
		if (!settings.hudEnabled) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.options.hudHidden) {
			return;
		}

		FateStatePayload payload = ClientFateState.latest();
		if (payload == null) {
			return;
		}

		renderAnnouncement(context, client, payload);

		if (payload.offerPending()) {
			renderOfferBanner(context, client, payload);
		} else {
			renderStatusBox(context, client, payload);
		}
	}

	private static void renderStatusBox(DrawContext context, MinecraftClient client, FateStatePayload payload) {
		int screenWidth = context.getScaledWindowWidth();
		int screenHeight = context.getScaledWindowHeight();

		Text title = Text.literal("FATE CARDS").formatted(Formatting.GOLD, Formatting.BOLD);
		Text status;
		if ("RUNNING".equals(payload.gameState())) {
			status = Text.literal("Следующий раунд через: " + formatTime(payload.secondsToNextRound()))
					.formatted(Formatting.WHITE);
		} else {
			status = Text.literal("Не запущен (клавиша N)").formatted(Formatting.GRAY);
		}

		int lineHeight = client.textRenderer.fontHeight + 2;
		int boxWidth = Math.max(client.textRenderer.getWidth(title), client.textRenderer.getWidth(status)) + PADDING * 2;
		int boxHeight = lineHeight * 2 + PADDING * 2;

		int x2 = screenWidth - MARGIN;
		int y2 = screenHeight - MARGIN;
		int x1 = x2 - boxWidth;
		int y1 = y2 - boxHeight;

		context.fill(x1, y1, x2, y2, 0xA0101010);
		context.fill(x1, y1, x2, y1 + 1, 0xFF3A3A3A);
		context.drawText(client.textRenderer, title, x1 + PADDING, y1 + PADDING, 0xFFFFFF, true);
		context.drawText(client.textRenderer, status, x1 + PADDING, y1 + PADDING + lineHeight, 0xFFFFFF, true);
	}

	private static void renderOfferBanner(DrawContext context, MinecraftClient client, FateStatePayload payload) {
		int screenWidth = context.getScaledWindowWidth();

		Text header = Text.literal("Судьба спрашивает: что достанется игроку " + payload.targetName() + "? ("
				+ payload.offerSecondsLeft() + "с)").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD);

		Text option1 = Text.literal("[,] " + payload.card1Name() + " — " + payload.card1Description())
				.formatted(Formatting.AQUA);
		Text option2 = Text.literal("[.] " + payload.card2Name() + " — " + payload.card2Description())
				.formatted(Formatting.YELLOW);

		int lineHeight = client.textRenderer.fontHeight + 2;
		int width = Math.max(client.textRenderer.getWidth(header),
				Math.max(client.textRenderer.getWidth(option1), client.textRenderer.getWidth(option2))) + PADDING * 2;
		width = Math.min(width, screenWidth - 20);
		int height = lineHeight * 3 + PADDING * 2;

		int x1 = screenWidth / 2 - width / 2;
		int y1 = 16;

		context.fill(x1, y1, x1 + width, y1 + height, 0xC0101010);
		context.fill(x1, y1, x1 + width, y1 + 1, 0xFFAA55FF);

		context.drawText(client.textRenderer, header, x1 + PADDING, y1 + PADDING, 0xFFFFFF, true);
		context.drawText(client.textRenderer, option1, x1 + PADDING, y1 + PADDING + lineHeight, 0xFFFFFF, true);
		context.drawText(client.textRenderer, option2, x1 + PADDING, y1 + PADDING + lineHeight * 2, 0xFFFFFF, true);
	}

	private static void renderAnnouncement(DrawContext context, MinecraftClient client, FateStatePayload payload) {
		String text = payload.lastAnnouncement();
		if (text == null || text.isEmpty()) {
			return;
		}
		if (!text.equals(lastShownAnnouncement)) {
			lastShownAnnouncement = text;
			announcementShownAtMs = System.currentTimeMillis();
		}
		if (System.currentTimeMillis() - announcementShownAtMs > 6000) {
			return;
		}
		int screenWidth = context.getScaledWindowWidth();
		int screenHeight = context.getScaledWindowHeight();
		Text line = Text.literal(text).formatted(Formatting.GOLD);
		int textWidth = client.textRenderer.getWidth(line);
		int x = screenWidth / 2 - textWidth / 2;
		int y = screenHeight - 64;
		context.fill(x - 6, y - 4, x + textWidth + 6, y + client.textRenderer.fontHeight + 4, 0xA0101010);
		context.drawText(client.textRenderer, line, x, y, 0xFFFFFF, true);
	}

	private static String formatTime(int totalSeconds) {
		int minutes = totalSeconds / 60;
		int seconds = totalSeconds % 60;
		return String.format("%02d:%02d", minutes, seconds);
	}
}
