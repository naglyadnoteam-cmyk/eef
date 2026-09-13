package com.naglyadno.speedhunt.client;

import com.naglyadno.speedhunt.network.GameStatePayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/** Рисует окошко Speedhunt в правом нижнем углу экрана. */
public final class HudOverlay {

	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
	private static final int PADDING = 4;
	private static final int MARGIN = 8;

	private HudOverlay() {
	}

	public static void render(DrawContext context) {
		ClientSettings settings = ClientSettings.get();
		if (!settings.hudEnabled) {
			return;
		}
		GameStatePayload payload = ClientGameState.latest();
		if (payload == null) {
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.options.hudHidden) {
			return;
		}

		List<Text> lines = buildLines(payload, settings);
		if (lines.isEmpty()) {
			return;
		}

		int screenWidth = context.getScaledWindowWidth();
		int screenHeight = context.getScaledWindowHeight();

		int lineHeight = client.textRenderer.fontHeight + 2;
		int boxWidth = 0;
		for (Text line : lines) {
			boxWidth = Math.max(boxWidth, client.textRenderer.getWidth(line));
		}
		boxWidth += PADDING * 2;
		int boxHeight = lines.size() * lineHeight + PADDING * 2;

		int x2 = screenWidth - MARGIN;
		int y2 = screenHeight - MARGIN;
		int x1 = x2 - boxWidth;
		int y1 = y2 - boxHeight;

		context.fill(x1, y1, x2, y2, 0xA0101010);
		context.fill(x1, y1, x2, y1 + 1, 0xFF3A3A3A);

		int textY = y1 + PADDING;
		for (Text line : lines) {
			context.drawText(client.textRenderer, line, x1 + PADDING, textY, 0xFFFFFF, true);
			textY += lineHeight;
		}
	}

	private static List<Text> buildLines(GameStatePayload payload, ClientSettings settings) {
		List<Text> lines = new ArrayList<>();
		lines.add(Text.literal("SPEEDHUNT").formatted(Formatting.GOLD, Formatting.BOLD));

		String role = payload.role();
		String state = payload.gameState();

		switch (state) {
			case "WAITING" -> lines.add(Text.literal("Ожидание игроков...").formatted(Formatting.GRAY));
			case "ASSIGNING" -> lines.add(Text.literal("Определение ролей... " + payload.countdownSeconds())
					.formatted(Formatting.YELLOW));
			case "GRACE" -> {
				addRoleLine(lines, role);
				lines.add(Text.literal("Неприкосновенность: " + payload.countdownSeconds() + "с")
						.formatted(Formatting.YELLOW));
			}
			case "RUNNING" -> {
				addRoleLine(lines, role);
				lines.add(Text.literal("Время: " + formatTime(payload.elapsedSeconds())).formatted(Formatting.WHITE));
				if (settings.trackerHudEnabled && payload.trackerActive() && "HUNTER".equals(role)) {
					String arrow = arrowFor(payload.trackerYaw());
					int dist = Math.round(payload.trackerDistance());
					lines.add(Text.literal(arrow + " " + dist + "м").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
				}
			}
			case "ENDED" -> {
				if (!payload.lastResultMessage().isEmpty()) {
					lines.add(Text.literal(payload.lastResultMessage()).formatted(Formatting.AQUA));
				}
				lines.add(Text.literal("Новый матч через: " + payload.countdownSeconds() + "с")
						.formatted(Formatting.GRAY));
			}
			default -> {
			}
		}

		return lines;
	}

	private static void addRoleLine(List<Text> lines, String role) {
		if ("SPEEDRUNNER".equals(role)) {
			lines.add(Text.literal("Роль: Спидраннер").formatted(Formatting.GREEN, Formatting.BOLD));
		} else if ("HUNTER".equals(role)) {
			lines.add(Text.literal("Роль: Охотник").formatted(Formatting.RED, Formatting.BOLD));
		} else {
			lines.add(Text.literal("Роль: Зритель").formatted(Formatting.GRAY));
		}
	}

	private static String formatTime(int totalSeconds) {
		int minutes = totalSeconds / 60;
		int seconds = totalSeconds % 60;
		return String.format("%02d:%02d", minutes, seconds);
	}

	private static String arrowFor(float relativeYaw) {
		float normalized = relativeYaw % 360f;
		if (normalized < 0) {
			normalized += 360f;
		}
		int index = Math.round(normalized / 45f) % 8;
		return ARROWS[index];
	}
}
