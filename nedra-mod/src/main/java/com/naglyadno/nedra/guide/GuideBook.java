package com.naglyadno.nedra.guide;

import com.naglyadno.nedra.NedraMod;
import com.naglyadno.nedra.config.NedraConfig;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.Rarity;

import java.util.ArrayList;
import java.util.List;

/**
 * Справочник "Недра". Страницы - переводимые строки (lang/*.json), поэтому книга сама
 * показывается на языке клиента и автоматически обновляется вместе с модом.
 */
public final class GuideBook {

	public static final int PAGE_COUNT = 19;

	private GuideBook() {
	}

	public static ItemStack create() {
		ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
		List<RawFilteredPair<Text>> pages = new ArrayList<>();
		for (int i = 1; i <= PAGE_COUNT; i++) {
			pages.add(RawFilteredPair.of(Text.translatable("guide.nedra.page." + i, pageArgs(i))));
		}
		WrittenBookContentComponent content = new WrittenBookContentComponent(
				RawFilteredPair.of("Недра"), "naglyadno.team", 0, pages, true);
		stack.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, content);
		// CUSTOM_NAME перекрывает название из заголовка книги и при этом переводится на язык клиента
		stack.set(DataComponentTypes.CUSTOM_NAME, Text.translatable("item.nedra.guide").styled(style -> style.withItalic(false)));
		stack.set(DataComponentTypes.RARITY, Rarity.UNCOMMON);
		return stack;
	}

	/** Числа на страницах берутся из конфига сервера, чтобы книга не расходилась с настройками. */
	private static Object[] pageArgs(int page) {
		NedraConfig config = NedraMod.config();
		return switch (page) {
			case 5 -> new Object[]{
					config.helmetLightReductionPercent, config.helmetReinforcedReductionPercent, config.helmetDeepsuitReductionPercent,
					config.tabletReductionPercent, formatTime(config.tabletDurationTicks / 20)};
			case 11 -> new Object[]{config.magnetiteInterferenceRadius};
			case 13 -> new Object[]{config.geophoneRadius};
			default -> new Object[0];
		};
	}

	private static String formatTime(int seconds) {
		return String.format("%d:%02d", seconds / 60, seconds % 60);
	}
}
