package com.naglyadno.speedhunt.client.gui;

import com.naglyadno.speedhunt.client.ClientSettings;
import com.naglyadno.speedhunt.network.RequestActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Меню Speedhunt, открывается по клавише (по умолчанию — см. настройки управления). */
public class SpeedHuntMenuScreen extends Screen {

	private final ClientSettings settings = ClientSettings.get();

	public SpeedHuntMenuScreen() {
		super(Text.literal("Speedhunt — настройки"));
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int y = this.height / 2 - 64;

		this.addDrawableChild(ButtonWidget.builder(
				Text.literal("Начать матч").formatted(Formatting.GREEN),
				button -> {
					ClientPlayNetworking.send(new RequestActionPayload(true));
					close();
				}
		).dimensions(centerX - 100, y, 200, 20).build());

		y += 24;
		this.addDrawableChild(ButtonWidget.builder(
				Text.literal("Остановить матч").formatted(Formatting.RED),
				button -> {
					ClientPlayNetworking.send(new RequestActionPayload(false));
					close();
				}
		).dimensions(centerX - 100, y, 200, 20).build());

		y += 32;
		this.addDrawableChild(ButtonWidget.builder(hudButtonText(), button -> {
			settings.hudEnabled = !settings.hudEnabled;
			button.setMessage(hudButtonText());
			settings.save();
		}).dimensions(centerX - 100, y, 200, 20).build());

		y += 24;
		this.addDrawableChild(ButtonWidget.builder(trackerButtonText(), button -> {
			settings.trackerHudEnabled = !settings.trackerHudEnabled;
			button.setMessage(trackerButtonText());
			settings.save();
		}).dimensions(centerX - 100, y, 200, 20).build());

		y += 32;
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Готово"), button -> close())
				.dimensions(centerX - 100, y, 200, 20).build());
	}

	private Text hudButtonText() {
		return Text.literal("HUD-окошко: " + (settings.hudEnabled ? "ВКЛ" : "ВЫКЛ"));
	}

	private Text trackerButtonText() {
		return Text.literal("Трекер спидраннера: " + (settings.trackerHudEnabled ? "ВКЛ" : "ВЫКЛ"));
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context, mouseX, mouseY, delta);
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 94, 0xFFFFFF);
		context.drawCenteredTextWithShadow(
				this.textRenderer,
				Text.literal("Нужно минимум 2 игрока онлайн. Результат придёт в чат.").formatted(Formatting.GRAY),
				this.width / 2, this.height / 2 + 68, 0xAAAAAA
		);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
