package com.naglyadno.nedra.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

/**
 * HUD давления: вертикальный манометр справа, красная виньетка с пульсом на высоких ярусах
 * и помехи поверх экрана отладки (F3). Всё рисуется примитивами - чётко при любом масштабе GUI.
 */
public final class PressureHud {

	private static final int BAR_W = 6;
	private static final int BAR_H = 84;
	private static final int EDGE = 10;
	private static final float[] THRESHOLDS = {10, 30, 55, 75, 92};
	private static final int[] TIER_COLORS = {0x8FD18F, 0x8FD18F, 0xE8D25A, 0xF0A04B, 0xF0603C, 0xFF3B3B};

	/** Опорные цвета шкалы по значению давления 0..100. */
	private static final float[] STOPS = {0, 30, 55, 75, 92, 100};
	private static final int[] STOP_COLORS = {0x3FAE5A, 0xB5D53B, 0xF2C230, 0xF08A33, 0xE5452F, 0xB3141B};

	private PressureHud() {
	}

	// ------------------------------------------------------------------ gauge

	public static void renderGauge(DrawContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.options.hudHidden) {
			return;
		}
		ClientPressureState.animate();
		float alpha = ClientPressureState.visibility();
		if (alpha <= 0.01f) {
			return;
		}
		TextRenderer font = client.textRenderer;
		float effective = ClientPressureState.effective();
		float raw = ClientPressureState.raw();
		int tier = ClientPressureState.tier();
		float pulse = ClientAmbience.pulse();

		int width = context.getScaledWindowWidth();
		int height = context.getScaledWindowHeight();
		int x1 = width - EDGE - BAR_W;
		int x2 = x1 + BAR_W;
		// низ правого края: там не бывает всплывающих уведомлений (они сверху) и таблицы счёта (по центру)
		int y2 = height - 52;
		int y1 = y2 - BAR_H;

		// корпус манометра: тёмная подложка, контур и блик
		context.fill(x1 - 3, y1 - 3, x2 + 3, y2 + 3, argb(0x05070A, 0.55f * alpha));
		context.fill(x1 - 2, y1 - 2, x2 + 2, y2 + 2, argb(0x2A2E36, 0.95f * alpha));
		context.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, argb(0x0B0D11, 0.95f * alpha));
		context.fill(x1 - 2, y1 - 2, x2 + 2, y1 - 1, argb(0x5A606C, 0.9f * alpha));

		// "сырое" давление без защиты - штриховка: эту часть сейчас забирает на себя защита
		int rawTop = y2 - Math.round(BAR_H * MathHelper.clamp(raw, 0, 100) / 100f);
		int effTop = y2 - Math.round(BAR_H * MathHelper.clamp(effective, 0, 100) / 100f);
		for (int y = rawTop; y < effTop; y++) {
			if (((y + (int) (System.currentTimeMillis() / 120)) & 1) == 0) {
				context.fill(x1, y, x2, y + 1, argb(0x9FB6D8, 0.28f * alpha));
			}
		}
		if (raw - effective > 1.5f) {
			context.fill(x1 - 1, rawTop, x2 + 1, rawTop + 1, argb(0xD6E4FF, 0.75f * alpha));
		}

		// заполнение: градиент от цвета текущего значения к более спокойному снизу
		if (effTop < y2) {
			int top = colorAt(effective);
			int bottom = colorAt(Math.max(0, effective - 40));
			float boost = 0.75f + 0.25f * pulse;
			context.fillGradient(x1, effTop, x2, y2, argb(brighten(top, pulse * 0.35f), boost * alpha),
					argb(bottom, 0.85f * alpha));
			context.fill(x1, effTop, x1 + 1, y2, argb(0xFFFFFF, 0.18f * alpha));
			context.fill(x1, effTop, x2, effTop + 1, argb(0xFFFFFF, 0.55f * alpha));
		}

		// риски порогов ярусов
		for (float threshold : THRESHOLDS) {
			int ty = y2 - Math.round(BAR_H * threshold / 100f);
			context.fill(x1 - 5, ty, x1 - 2, ty + 1, argb(0xC8CCD4, 0.55f * alpha));
		}

		// подписи слева от шкалы
		int tierColor = TIER_COLORS[MathHelper.clamp(tier, 0, 5)];
		Text value = Text.literal(Math.round(effective) + "%");
		int valueX = x1 - 8 - font.getWidth(value);
		context.drawText(font, value, valueX, y1 - 1, argb(brighten(tierColor, pulse * 0.4f), alpha), true);

		Text tierName = Text.translatable("hud.nedra.tier." + MathHelper.clamp(tier, 0, 5));
		drawSmall(context, font, tierName, x1 - 8, y1 + 9, argb(0xB8BDC6, alpha), true);

		int footerY = y2 + 6;
		Text depth = Text.translatable("hud.nedra.depth", client.player.getBlockY());
		drawSmall(context, font, depth, x2 + 1, footerY, argb(0x9AA0AA, alpha), true);
		int protection = ClientPressureState.protection();
		if (protection > 0) {
			Text shield = Text.translatable("hud.nedra.protection", protection);
			drawSmall(context, font, shield, x2 + 1, footerY + 8, argb(0x8FB7FF, alpha), true);
		}
	}

	/** Мелкий текст (75%), выровненный по правому краю rightX. */
	private static void drawSmall(DrawContext context, TextRenderer font, Text text, int rightX, int y, int color, boolean right) {
		float scale = 0.75f;
		int w = font.getWidth(text);
		context.getMatrices().pushMatrix();
		context.getMatrices().translate(right ? rightX - w * scale : rightX, y);
		context.getMatrices().scale(scale, scale);
		context.drawText(font, text, 0, 0, color, true);
		context.getMatrices().popMatrix();
	}

	// ------------------------------------------------------------------ vignette

	public static void renderVignette(DrawContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.options.hudHidden) {
			return;
		}
		int tier = ClientPressureState.tier();
		if (tier < 4) {
			return;
		}
		float pulse = ClientAmbience.pulse();
		boolean critical = tier >= 5;
		// виньетка должна читаться и в тёмной пещере, поэтому насыщенный красный и заметная непрозрачность по краю
		float strength = (critical ? 0.62f : 0.42f) + pulse * (critical ? 0.3f : 0.2f);
		int color = critical ? 0x8C0505 : 0x6E0606;
		int width = context.getScaledWindowWidth();
		int height = context.getScaledWindowHeight();
		int thickness = (int) (Math.min(width, height) * (critical ? 0.38f : 0.3f));
		int steps = 18;
		int band = Math.max(1, thickness / steps);
		for (int i = 0; i < steps; i++) {
			float falloff = 1f - (float) i / steps;
			int argbColor = argb(color, strength * falloff * falloff);
			int o = i * band;
			context.fill(o, o, width - o, o + band, argbColor);
			context.fill(o, height - o - band, width - o, height - o, argbColor);
			context.fill(o, o + band, o + band, height - o - band, argbColor);
			context.fill(width - o - band, o + band, width - o, height - o - band, argbColor);
		}
		if (critical) {
			context.fill(0, 0, width, height, argb(0x3A0000, 0.08f + pulse * 0.1f));
		}
	}

	// ------------------------------------------------------------------ F3 interference

	/** Вызывается миксином после отрисовки экрана отладки: высокое давление "сбивает" приборы. */
	public static void renderDebugInterference(DrawContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		int tier = ClientPressureState.tier();
		if (client.player == null || tier < 3 || !client.getDebugHud().shouldShowDebugHud()) {
			return;
		}
		int width = context.getScaledWindowWidth();
		int height = context.getScaledWindowHeight();
		// картинка помех меняется ~11 раз в секунду, а не каждый кадр - иначе это мельтешение
		Random random = Random.create(System.currentTimeMillis() / 90);
		int lines = (tier - 2) * 7;
		for (int i = 0; i < lines; i++) {
			boolean leftColumn = random.nextBoolean();
			int w = 40 + random.nextInt(width / 3);
			int x = leftColumn ? random.nextInt(40) : width - w - random.nextInt(40);
			int y = random.nextInt(height);
			int h = 1 + random.nextInt(tier >= 5 ? 4 : 2);
			if (random.nextInt(3) == 0) {
				context.fill(x, y, x + w, y + h, argb(0x000000, 0.55f + random.nextFloat() * 0.3f));
			} else {
				int gray = 150 + random.nextInt(90);
				context.fill(x, y, x + w, y + h, argb(gray << 16 | gray << 8 | gray, 0.2f + random.nextFloat() * 0.35f));
			}
		}
		if (tier >= 5 && random.nextInt(4) == 0) {
			int y = random.nextInt(height);
			context.fill(0, y, width, y + 6 + random.nextInt(10), argb(0x000000, 0.65f));
		}
	}

	// ------------------------------------------------------------------ colour helpers

	private static int colorAt(float value) {
		for (int i = 0; i < STOPS.length - 1; i++) {
			if (value <= STOPS[i + 1]) {
				float t = (value - STOPS[i]) / (STOPS[i + 1] - STOPS[i]);
				return lerpColor(STOP_COLORS[i], STOP_COLORS[i + 1], MathHelper.clamp(t, 0, 1));
			}
		}
		return STOP_COLORS[STOP_COLORS.length - 1];
	}

	private static int lerpColor(int a, int b, float t) {
		int r = (int) MathHelper.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
		int g = (int) MathHelper.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
		int bl = (int) MathHelper.lerp(t, a & 0xFF, b & 0xFF);
		return r << 16 | g << 8 | bl;
	}

	private static int brighten(int rgb, float amount) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		r += (int) ((255 - r) * amount);
		g += (int) ((255 - g) * amount);
		b += (int) ((255 - b) * amount);
		return r << 16 | g << 8 | b;
	}

	private static int argb(int rgb, float alpha) {
		int a = MathHelper.clamp((int) (alpha * 255f), 0, 255);
		return a << 24 | (rgb & 0xFFFFFF);
	}
}
