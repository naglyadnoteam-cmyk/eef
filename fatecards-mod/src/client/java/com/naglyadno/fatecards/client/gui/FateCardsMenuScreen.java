package com.naglyadno.fatecards.client.gui;

import com.naglyadno.fatecards.client.ClientSettings;
import com.naglyadno.fatecards.network.RequestActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Меню Fate Cards, открывается по клавише (по умолчанию N). */
public class FateCardsMenuScreen extends Screen {

	private final ClientSettings settings = ClientSettings.get();

	public FateCardsMenuScreen() {
		super(Text.literal("Fate Cards — настройки"));
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int y = this.height / 2 - 52;

		this.addDrawableChild(ButtonWidget.builder(
				Text.literal("Начать режим").formatted(Formatting.GREEN),
				button -> {
					ClientPlayNetworking.send(new RequestActionPayload(true));
					close();
				}
		).dimensions(centerX - 100, y, 200, 20).build());

		y += 24;
		this.addDrawableChild(ButtonWidget.builder(
				Text.literal("Остановить режим").formatted(Formatting.RED),
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

		y += 32;
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Готово"), button -> close())
				.dimensions(centerX - 100, y, 200, 20).build());
	}

	private Text hudButtonText() {
		return Text.literal("HUD-окошко: " + (settings.hudEnabled ? "ВКЛ" : "ВЫКЛ"));
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context, mouseX, mouseY, delta);
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 80, 0xFFFFFF);
		context.drawCenteredTextWithShadow(
				this.textRenderer,
				Text.literal("Нужно минимум 2 игрока онлайн. Раунд карт — раз в 5 минут (настраивается).")
						.formatted(Formatting.GRAY),
				this.width / 2, this.height / 2 + 44, 0xAAAAAA
		);
		context.drawCenteredTextWithShadow(
				this.textRenderer,
				Text.literal("Выбор карты: клавиши , и . (запятая/точка)").formatted(Formatting.GRAY),
				this.width / 2, this.height / 2 + 58, 0xAAAAAA
		);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
