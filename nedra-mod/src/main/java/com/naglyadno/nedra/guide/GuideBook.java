package com.naglyadno.nedra.guide;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Справочник "Недра" - выдаётся каждому игроку один раз при первом заходе на сервер с модом. */
public final class GuideBook {

	private GuideBook() {
	}

	public static ItemStack create() {
		ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
		List<RawFilteredPair<Text>> pages = new ArrayList<>();
		for (String page : pages()) {
			pages.add(RawFilteredPair.of(Text.literal(page)));
		}
		WrittenBookContentComponent content = new WrittenBookContentComponent(
				RawFilteredPair.of("Недра"), "naglyadno.team", 0, pages, true);
		stack.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, content);
		stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Недра: справочник шахтёра"));
		return stack;
	}

	private static String[] pages() {
		return new String[]{
				"§l§6НЕДРА§r\n\nСправочник шахтёра.\n\nЧем глубже вы копаете - тем сильнее меняется мир вокруг. Эта книга поможет выжить.",

				"§l§9Давление§r\n\nСправа на экране - шкала давления. Растёт с глубиной.\n\nНа высоких значениях: экран краснеет, слышно сердцебиение, тяжелее копать, а на F3 начинаются помехи.",

				"§l§9Защита от давления§r\n\nШлемы снижают давление:\n§7- Лёгкий шахтёрский шлем\n§7- Усиленный шлем\n§7- Шлем глубинного скафандра\n\nЧем лучше шлем - тем меньше давит на голову.",

				"§l§9Таблетки§r\n\nТаблетка от давления (крафтится из глубинного мха) временно снижает давление сверху защиты шлема. Мох растёт в пещерах на камне.",

				"§l§cОпасности: обвалы§r\n\nНа глубине копка рядом с нестабильным камнем может вызвать обвал.\n\nПредупреждение - треск и звук осыпающихся камней. Отойдите!",

				"§l§cОпасности: течения§r\n\nВ пещерах встречаются блоки-источники потоков воздуха - они толкают игрока. Будьте осторожны у обрывов.",

				"§l§bРуды на слух§r\n\nЭхо-руда почти не видна глазом, но издаёт тихий звон рядом. Прислушивайтесь!\n\nГеофон (крафтится отдельно) помогает находить такие руды по направлению.",

				"§l§7Продолжение следует...§r\n\nЭто первая часть справочника. Скоро здесь появятся главы про новые биомы, подземные реки, озёра и редкие пещерные океаны, магнитную руду и подземные поселения.",
		};
	}
}
