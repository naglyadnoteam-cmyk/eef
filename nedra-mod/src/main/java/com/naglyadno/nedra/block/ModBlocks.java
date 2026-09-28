package com.naglyadno.nedra.block;

import com.naglyadno.nedra.NedraMod;
import com.naglyadno.nedra.block.entity.EchoOreBlockEntity;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.function.Function;

public final class ModBlocks {

	private ModBlocks() {
	}

	public static final Block LUMENITE_ORE = registerWithItem("lumenite_ore", Block::new,
			AbstractBlock.Settings.create().mapColor(MapColor.LIGHT_BLUE).strength(4.0f, 6.0f).requiresTool()
					.luminance(state -> 8).sounds(net.minecraft.sound.BlockSoundGroup.DEEPSLATE));

	public static final Block MAGNETITE_ORE = registerWithItem("magnetite_ore", Block::new,
			AbstractBlock.Settings.create().mapColor(MapColor.IRON_GRAY).strength(4.5f, 6.0f).requiresTool()
					.sounds(net.minecraft.sound.BlockSoundGroup.DEEPSLATE));

	public static final Block ECHO_ORE = registerWithItem("echo_ore", EchoOreBlock::new,
			AbstractBlock.Settings.create().mapColor(MapColor.BLACK).strength(4.5f, 6.5f).requiresTool()
					.sounds(net.minecraft.sound.BlockSoundGroup.DEEPSLATE));

	public static final Block UNSTABLE_STONE = registerWithItem("unstable_stone", Block::new,
			AbstractBlock.Settings.create().mapColor(MapColor.STONE_GRAY).strength(1.2f, 2.0f)
					.sounds(net.minecraft.sound.BlockSoundGroup.STONE));

	public static final Block CURRENT_VENT = registerWithItem("current_vent", Block::new,
			AbstractBlock.Settings.create().mapColor(MapColor.TERRACOTTA_CYAN).strength(2.5f, 4.0f)
					.sounds(net.minecraft.sound.BlockSoundGroup.BASALT).nonOpaque());

	public static final Block DEEPMOSS = registerWithItem("deepmoss", DeepmossBlock::new,
			AbstractBlock.Settings.create().mapColor(MapColor.DARK_GREEN).strength(0.3f)
					.sounds(net.minecraft.sound.BlockSoundGroup.MOSS_BLOCK));

	public static BlockEntityType<EchoOreBlockEntity> ECHO_ORE_ENTITY;

	private static Block registerWithItem(String path, Function<AbstractBlock.Settings, Block> factory,
			AbstractBlock.Settings settings) {
		RegistryKey<Block> key = blockKey(path);
		Block block = factory.apply(settings.registryKey(key));
		Registry.register(Registries.BLOCK, key, block);
		registerBlockItem(path, block);
		return block;
	}

	private static void registerBlockItem(String path, Block block) {
		RegistryKey<Item> itemKey = itemKey(path);
		BlockItem item = new BlockItem(block, new Item.Settings().registryKey(itemKey));
		Registry.register(Registries.ITEM, itemKey, item);
	}

	private static RegistryKey<Block> blockKey(String path) {
		return RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(NedraMod.MOD_ID, path));
	}

	private static RegistryKey<Item> itemKey(String path) {
		return RegistryKey.of(RegistryKeys.ITEM, Identifier.of(NedraMod.MOD_ID, path));
	}

	public static void init() {
		ECHO_ORE_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE,
				Identifier.of(NedraMod.MOD_ID, "echo_ore"),
				net.minecraft.block.entity.BlockEntityType.Builder.create(EchoOreBlockEntity::new, ECHO_ORE).build());
	}
}
