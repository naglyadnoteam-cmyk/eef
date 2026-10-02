package com.naglyadno.nedra.item;

import com.naglyadno.nedra.NedraMod;
import net.minecraft.item.Item;
import net.minecraft.item.equipment.ArmorMaterial;
import net.minecraft.item.equipment.EquipmentAsset;
import net.minecraft.item.equipment.EquipmentAssetKeys;
import net.minecraft.item.equipment.EquipmentType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

import java.util.Map;

/**
 * Материалы шахтёрских шлемов. Каждый имеет свою модель на голове (assets/nedra/equipment/*.json)
 * и свой тег ремонтного материала (data/nedra/tags/item/repairs_*.json).
 */
public final class ModArmorMaterials {

	private ModArmorMaterials() {
	}

	public static final ArmorMaterial MINER = new ArmorMaterial(
			15, Map.of(EquipmentType.HELMET, 2), 14, SoundEvents.ITEM_ARMOR_EQUIP_IRON,
			0.0f, 0.0f, repairTag("repairs_miner_helmet"), asset("miner"));

	public static final ArmorMaterial REINFORCED = new ArmorMaterial(
			26, Map.of(EquipmentType.HELMET, 3), 10, SoundEvents.ITEM_ARMOR_EQUIP_IRON,
			1.0f, 0.0f, repairTag("repairs_reinforced_helmet"), asset("reinforced"));

	public static final ArmorMaterial DEEPSUIT = new ArmorMaterial(
			35, Map.of(EquipmentType.HELMET, 3), 12, SoundEvents.ITEM_ARMOR_EQUIP_TURTLE,
			2.0f, 0.05f, repairTag("repairs_deepsuit_helmet"), asset("deepsuit"));

	private static TagKey<Item> repairTag(String path) {
		return TagKey.of(RegistryKeys.ITEM, Identifier.of(NedraMod.MOD_ID, path));
	}

	private static RegistryKey<EquipmentAsset> asset(String path) {
		return RegistryKey.of(EquipmentAssetKeys.REGISTRY_KEY, Identifier.of(NedraMod.MOD_ID, path));
	}
}
