package com.naglyadno.nedra.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Random;

/** Правая вертикальная шкала давления + красная виньетка/пульс на высоких ярусах + "шум" поверх F3. */
public final class PressureHud {

	private static final int BAR_WIDTH = 10;
	private static final int BAR_HEIGHT = 90;
	private static final int MARGIN = 10;
	private static final Random GLITCH_RANDOM = new Random();

	private PressureHud() {
	}

	public static void render(DrawContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.options.hudHidden) {
			return;
		}

		double pressure = ClientPressureState.pressure();
		int tier = ClientPressureState.tier();

		renderGauge(context, client, pressure, tier);

		if (tier >= 4) {
			renderVignette(context, tier);
		}
		if (tier >= 3) {
			renderDebugStatic(context, tier);
		}
	}

	private static void renderGauge(DrawContext context, MinecraftClient client, double pressure, int tier) {
		int screenWidth = context.getScaledWindowWidth();
		int screenHeight = context.getScaledWindowHeight();
		int x1 = screenWidth - MARGIN - BAR_WIDTH;
		int y1 = screenHeight / 2 - BAR_HEIGHT / 2;
		int x2 = x1 + BAR_WIDTH;
		int y2 = y1 + BAR_HEIGHT;

		context.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0x90000000);

		int filled = (int) Math.round(BAR_HEIGHT * (pressure / 100.0));
		int fillTop = y2 - filled;
		int color = colorFor(pressure);
		context.fill(x1, fillTop, x2, y2, color);

		Text label = Text.literal((int) Math.round(pressure) + "%").formatted(tierFormatting(tier));
		int labelWidth = client.textRenderer.getWidth(label);
		context.drawText(client.textRenderer, label, x1 - labelWidth - 4, y1 - 2, 0xFFFFFF, true);

		Text icon = Text.literal("Давление").formatted(Formatting.GRAY);
		context.drawText(client.textRenderer, icon, x1 - client.textRenderer.getWidth(icon), y2 + 4, 0xFFFFFF, true);
	}

	private static int colorFor(double pressure) {
		if (pressure < 30) return 0xFF3FA34D;
		if (pressure < 55) return 0xFFC9A227;
		if (pressure < 75) return 0xFFD9732A;
		if (pressure < 92) return 0xFFC0392B;
		return 0xFFFF1E1E;
	}

	private static Formatting tierFormatting(int tier) {
		return switch (tier) {
			case 0, 1 -> Formatting.GREEN;
			case 2 -> Formatting.YELLOW;
			case 3 -> Formatting.GOLD;
			case 4 -> Formatting.RED;
			default -> Formatting.DARK_RED;
		};
	}

	private static void renderVignette(DrawContext context, int tier) {
		int screenWidth = context.getScaledWindowWidth();
		int screenHeight = context.getScaledWindowHeight();
		long time = System.currentTimeMillis();
		double pulse = 0.5 + 0.5 * Math.sin(time / (tier >= 5 ? 220.0 : 380.0));
		int alpha = (int) (((tier - 3) * 25) + pulse * 30);
		alpha = Math.max(0, Math.min(140, alpha));
		int color = (alpha << 24) | 0x660000;
		int thickness = screenHeight / 6;

		context.fillGradient(0, 0, screenWidth, thickness, color, 0x00660000);
		context.fillGradient(0, screenHeight - thickness, screenWidth, screenHeight, 0x00660000, color);
		context.fillGradient(0, 0, thickness, screenHeight, color, 0x00660000);
		context.fillGradient(screenWidth - thickness, 0, screenWidth, screenHeight, 0x00660000, color);
	}

	private static void renderDebugStatic(DrawContext context, int tier) {
		int blocksCount = 6 + tier * 2;
		int baseAlpha = 40 + tier * 15;
		for (int i = 0; i < blocksCount; i++) {
			int w = 20 + GLITCH_RANDOM.nextInt(80);
			int h = 6 + GLITCH_RANDOM.nextInt(6);
			int x = GLITCH_RANDOM.nextInt(180);
			int y = GLITCH_RANDOM.nextInt(160);
			int alpha = Math.min(200, baseAlpha + GLITCH_RANDOM.nextInt(60));
			int gray = 180 + GLITCH_RANDOM.nextInt(60);
			int color = (alpha << 24) | (gray << 16) | (gray << 8) | gray;
			context.fill(x, y, x + w, y + h, color);
		}
	}
}
