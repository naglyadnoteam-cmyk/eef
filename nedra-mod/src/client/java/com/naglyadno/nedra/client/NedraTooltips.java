package com.naglyadno.nedra.client;

import com.naglyadno.nedra.component.ModComponents;
import com.naglyadno.nedra.block.ModBlocks;
import com.naglyadno.nedra.item.ModItems;
import com.naglyadno.nedra.network.ConfigSyncPayload;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/** Подсказки к предметам мода. Числа берутся из серверного конфига (ConfigSyncPayload). */
public final class NedraTooltips {

	private NedraTooltips() {
	}

	public static void append(ItemStack stack, List<Text> lines) {
		List<Text> extra = new ArrayList<>();
		Item item = stack.getItem();
		ConfigSyncPayload config = ClientPressureState.config();

		if (item == ModItems.HELMET_LIGHT) {
			helmet(extra, config.helmetLight(), "tooltip.nedra.helmet_light");
		} else if (item == ModItems.HELMET_REINFORCED) {
			helmet(extra, config.helmetReinforced(), "tooltip.nedra.helmet_reinforced");
		} else if (item == ModItems.HELMET_DEEPSUIT) {
			helmet(extra, config.helmetDeepsuit(), "tooltip.nedra.helmet_deepsuit");
		} else if (item == ModItems.PRESSURE_TABLET) {
			int seconds = config.tabletSeconds();
			String time = String.format("%d:%02d", seconds / 60, seconds % 60);
			extra.add(Text.translatable("tooltip.nedra.pressure_tablet", config.tabletReduction(), time).formatted(Formatting.BLUE));
			extra.add(Text.translatable("tooltip.nedra.pressure_tablet.hint").formatted(Formatting.DARK_GRAY));
		} else if (item == ModItems.GEOPHONE) {
			extra.add(Text.translatable("tooltip.nedra.geophone").formatted(Formatting.GRAY));
			extra.add(Text.translatable("tooltip.nedra.geophone.radius", config.geophoneRadius()).formatted(Formatting.DARK_GRAY));
		} else if (item == ModItems.LUMENITE_CRYSTAL) {
			extra.add(flavor("tooltip.nedra.lumenite_crystal"));
		} else if (item == ModItems.RAW_MAGNETITE || item == ModItems.MAGNETITE_INGOT) {
			extra.add(flavor("tooltip.nedra.magnetite"));
		} else if (item == ModItems.RESONANT_SHARD) {
			extra.add(flavor("tooltip.nedra.resonant_shard"));
		} else if (item == ModItems.DEEPMOSS_CLUMP) {
			extra.add(flavor("tooltip.nedra.deepmoss_clump"));
		} else if (item == ModItems.FROST_SHARD) {
			extra.add(flavor("tooltip.nedra.frost_shard"));
		} else if (item == ModBlocks.FROST_LAMP.asItem()) {
			extra.add(Text.translatable("tooltip.nedra.frost_lamp").formatted(Formatting.AQUA));
		} else if (item == Items.COMPASS && Boolean.TRUE.equals(stack.get(ModComponents.MAGNETIZED))) {
			extra.add(Text.translatable("tooltip.nedra.magnetized").formatted(Formatting.RED));
		}

		if (!extra.isEmpty()) {
			lines.addAll(Math.min(1, lines.size()), extra);
		}
	}

	private static void helmet(List<Text> extra, int percent, String flavorKey) {
		extra.add(Text.translatable("tooltip.nedra.helmet.protection", percent).formatted(Formatting.BLUE));
		extra.add(flavor(flavorKey));
	}

	private static Text flavor(String key) {
		return Text.translatable(key).formatted(Formatting.DARK_GRAY, Formatting.ITALIC);
	}
}
