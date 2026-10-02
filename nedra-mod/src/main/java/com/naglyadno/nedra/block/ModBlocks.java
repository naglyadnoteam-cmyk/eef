package com.naglyadno.nedra.block;

import com.naglyadno.nedra.NedraMod;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.ExperienceDroppingBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.enums.NoteBlockInstrument;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.intprovider.UniformIntProvider;

import java.util.function.Function;

public final class ModBlocks {

	private ModBlocks() {
	}

	private static AbstractBlock.Settings oreSettings(MapColor color) {
		return AbstractBlock.Settings.create().mapColor(color).instrument(NoteBlockInstrument.BASEDRUM)
				.requiresTool().strength(4.5f, 3.0f).sounds(BlockSoundGroup.DEEPSLATE);
	}

	public static final Block LUMENITE_ORE = registerWithItem("lumenite_ore",
			settings -> new GlowingOreBlock(UniformIntProvider.create(2, 5), settings),
			oreSettings(MapColor.DIAMOND_BLUE).luminance(state -> 7));

	public static final Block MAGNETITE_ORE = registerWithItem("magnetite_ore",
			settings -> new ExperienceDroppingBlock(UniformIntProvider.create(1, 3), settings),
			oreSettings(MapColor.DEEPSLATE_GRAY));

	public static final Block ECHO_ORE = registerWithItem("echo_ore",
			settings -> new EchoOreBlock(UniformIntProvider.create(3, 7), settings),
			oreSettings(MapColor.DEEPSLATE_GRAY));

	public static final Block UNSTABLE_STONE = registerWithItem("unstable_stone", UnstableStoneBlock::new,
			AbstractBlock.Settings.create().mapColor(MapColor.STONE_GRAY).instrument(NoteBlockInstrument.BASEDRUM)
					.strength(1.2f, 2.0f).sounds(BlockSoundGroup.TUFF));

	public static final Block CURRENT_VENT = registerWithItem("current_vent", CurrentVentBlock::new,
			AbstractBlock.Settings.create().mapColor(MapColor.TERRACOTTA_CYAN).instrument(NoteBlockInstrument.BASEDRUM)
					.requiresTool().strength(2.5f, 4.0f).sounds(BlockSoundGroup.BASALT));

	public static final Block DEEPMOSS = registerWithItem("deepmoss", DeepmossBlock::new,
			AbstractBlock.Settings.create().mapColor(MapColor.CYAN).strength(0.3f)
					.luminance(state -> 3).sounds(BlockSoundGroup.MOSS_BLOCK));

	public static final Block LUMENITE_LAMP = registerWithItem("lumenite_lamp", Block::new,
			AbstractBlock.Settings.create().mapColor(MapColor.DIAMOND_BLUE).strength(0.6f)
					.luminance(state -> 15).sounds(BlockSoundGroup.AMETHYST_BLOCK));

	private static Block registerWithItem(String path, Function<AbstractBlock.Settings, Block> factory,
			AbstractBlock.Settings settings) {
		RegistryKey<Block> key = RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(NedraMod.MOD_ID, path));
		Block block = factory.apply(settings.registryKey(key));
		Registry.register(Registries.BLOCK, key, block);
		RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(NedraMod.MOD_ID, path));
		Registry.register(Registries.ITEM, itemKey,
				new BlockItem(block, new Item.Settings().registryKey(itemKey).useBlockPrefixedTranslationKey()));
		return block;
	}

	public static void init() {
		// регистрация выполняется в статических полях
	}
}
