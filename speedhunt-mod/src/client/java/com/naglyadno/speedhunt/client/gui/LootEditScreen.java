package com.naglyadno.speedhunt.client.gui;

import com.naglyadno.speedhunt.client.ClientGameState;
import com.naglyadno.speedhunt.network.GameStatePayload;
import com.naglyadno.speedhunt.network.SetLoadoutPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Экран выбора стартового лута для роли (как в творческом режиме, но
 * ограниченный 9 ячейками). Открывается из меню Speedhunt, работает только
 * пока матч не начат — сервер сам это проверит при сохранении.
 */
public class LootEditScreen extends Screen {

	private static final int SLOT_SIZE = 18;
	private static final int GRID_COLUMNS = 9;
	private static final int GRID_ROWS = 5;
	private static final int LOADOUT_SLOTS = 9;

	private final Screen parent;
	private final String role;
	private final List<Item> allItems;
	private List<Item> filtered;
	private TextFieldWidget searchField;
	private int scrollRow;

	private final List<ItemStack> loadout = new ArrayList<>();
	private boolean enabled;

	private int gridX;
	private int gridY;
	private int loadoutX;
	private int loadoutY;

	public LootEditScreen(Screen parent, String role) {
		super(Text.literal("HUNTER".equals(role) ? "Лут: Охотник" : "Лут: Спидраннер"));
		this.parent = parent;
		this.role = role;
		this.allItems = Registries.ITEM.stream()
				.filter(item -> item != Items.AIR)
				.collect(Collectors.toList());
		this.filtered = this.allItems;

		GameStatePayload state = ClientGameState.latest();
		this.enabled = state == null || ("HUNTER".equals(role) ? state.hunterLootEnabled() : state.speedrunnerLootEnabled());
	}

	@Override
	protected void init() {
		gridX = this.width / 2 - (GRID_COLUMNS * SLOT_SIZE) / 2;
		gridY = 66;
		loadoutX = this.width / 2 - (LOADOUT_SLOTS * SLOT_SIZE) / 2;
		loadoutY = gridY + GRID_ROWS * SLOT_SIZE + 30;

		this.addDrawableChild(ButtonWidget.builder(enabledButtonText(), button -> {
			enabled = !enabled;
			button.setMessage(enabledButtonText());
		}).dimensions(this.width / 2 - 100, 22, 200, 20).build());

		searchField = new TextFieldWidget(this.textRenderer, gridX, 46, GRID_COLUMNS * SLOT_SIZE, 16,
				Text.literal("Поиск"));
		searchField.setChangedListener(this::onSearchChanged);
		this.addDrawableChild(searchField);
		this.setInitialFocus(searchField);

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Сохранить").formatted(Formatting.GREEN), b -> save())
				.dimensions(this.width / 2 - 104, loadoutY + SLOT_SIZE + 14, 100, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Отмена"), b -> this.client.setScreen(parent))
				.dimensions(this.width / 2 + 4, loadoutY + SLOT_SIZE + 14, 100, 20).build());
	}

	private Text enabledButtonText() {
		return Text.literal("Выдавать лут при старте: " + (enabled ? "ВКЛ" : "ВЫКЛ"))
				.formatted(enabled ? Formatting.GREEN : Formatting.RED);
	}

	private void onSearchChanged(String query) {
		String q = query.toLowerCase();
		if (q.isBlank()) {
			filtered = allItems;
		} else {
			filtered = allItems.stream()
					.filter(item -> item.getName().getString().toLowerCase().contains(q)
							|| Registries.ITEM.getId(item).getPath().contains(q))
					.collect(Collectors.toList());
		}
		scrollRow = 0;
	}

	private void save() {
		List<String> ids = new ArrayList<>();
		List<Integer> counts = new ArrayList<>();
		for (ItemStack stack : loadout) {
			ids.add(Registries.ITEM.getId(stack.getItem()).toString());
			counts.add(stack.getCount());
		}
		ClientPlayNetworking.send(new SetLoadoutPayload(role, ids, counts, enabled));
		this.client.setScreen(parent);
	}

	private void addToLoadout(Item item) {
		for (ItemStack stack : loadout) {
			if (stack.getItem() == item && stack.getCount() < stack.getMaxCount()) {
				stack.increment(1);
				return;
			}
		}
		if (loadout.size() < LOADOUT_SLOTS) {
			loadout.add(new ItemStack(item, 1));
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (mouseX >= gridX && mouseY >= gridY
				&& mouseX < gridX + GRID_COLUMNS * SLOT_SIZE && mouseY < gridY + GRID_ROWS * SLOT_SIZE) {
			int col = (int) ((mouseX - gridX) / SLOT_SIZE);
			int row = (int) ((mouseY - gridY) / SLOT_SIZE);
			int index = (row + scrollRow) * GRID_COLUMNS + col;
			if (index >= 0 && index < filtered.size()) {
				addToLoadout(filtered.get(index));
				return true;
			}
		}

		if (mouseX >= loadoutX && mouseY >= loadoutY
				&& mouseX < loadoutX + LOADOUT_SLOTS * SLOT_SIZE && mouseY < loadoutY + SLOT_SIZE) {
			int lcol = (int) ((mouseX - loadoutX) / SLOT_SIZE);
			if (lcol < loadout.size()) {
				ItemStack stack = loadout.get(lcol);
				if (button == 1 || stack.getCount() <= 1) {
					loadout.remove(lcol);
				} else {
					stack.decrement(1);
				}
				return true;
			}
		}

		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int maxRow = Math.max(0, (int) Math.ceil(filtered.size() / (double) GRID_COLUMNS) - GRID_ROWS);
		scrollRow = Math.max(0, Math.min(maxRow, scrollRow - (int) Math.signum(verticalAmount)));
		return true;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context, mouseX, mouseY, delta);
		super.render(context, mouseX, mouseY, delta);

		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFF);

		for (int row = 0; row < GRID_ROWS; row++) {
			for (int col = 0; col < GRID_COLUMNS; col++) {
				int index = (row + scrollRow) * GRID_COLUMNS + col;
				int x = gridX + col * SLOT_SIZE;
				int y = gridY + row * SLOT_SIZE;
				context.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0x55000000);
				if (index < filtered.size()) {
					ItemStack stack = new ItemStack(filtered.get(index));
					context.drawItem(stack, x + 1, y + 1);
					if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
						context.drawTooltip(this.textRenderer, stack.getName(), mouseX, mouseY);
					}
				}
			}
		}

		context.drawTextWithShadow(this.textRenderer,
				Text.literal("Стартовый лут (клик — минус 1, ПКМ — убрать целиком):"),
				loadoutX, loadoutY - 12, 0xAAAAAA);

		for (int i = 0; i < LOADOUT_SLOTS; i++) {
			int x = loadoutX + i * SLOT_SIZE;
			context.fill(x, loadoutY, x + SLOT_SIZE, loadoutY + SLOT_SIZE, 0x55000000);
			if (i < loadout.size()) {
				context.drawItemInSlot(this.textRenderer, loadout.get(i), x + 1, loadoutY + 1);
			}
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
