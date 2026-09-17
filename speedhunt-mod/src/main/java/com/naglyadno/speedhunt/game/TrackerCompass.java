package com.naglyadno.speedhunt.game;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;

import java.util.Optional;

/**
 * Обычный компас, но вместо лодстоуна постоянно перенацеливается сервером
 * на текущее положение игрока нужной роли (см. GameManager#tickCompasses).
 */
public final class TrackerCompass {

	private static final String MARKER_KEY = "speedhunt_tracks";

	private TrackerCompass() {
	}

	/** Создаёт компас, указывающий на игрока с ролью {@code tracks}. */
	public static ItemStack create(Role tracks) {
		ItemStack stack = new ItemStack(Items.COMPASS);

		NbtCompound nbt = new NbtCompound();
		nbt.putString(MARKER_KEY, tracks.name());
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));

		String label = tracks == Role.SPEEDRUNNER ? "Компас: Спидраннер" : "Компас: Охотник";
		Formatting color = tracks == Role.SPEEDRUNNER ? Formatting.GREEN : Formatting.RED;
		stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(label).formatted(color, Formatting.ITALIC));

		return stack;
	}

	public static boolean tracksRole(ItemStack stack, Role role) {
		if (!stack.isOf(Items.COMPASS)) {
			return false;
		}
		NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
		if (data == null) {
			return false;
		}
		return role.name().equals(data.copyNbt().getString(MARKER_KEY));
	}

	public static void pointAt(ItemStack stack, ServerWorld world, BlockPos pos) {
		stack.set(
				DataComponentTypes.LODESTONE_TRACKER,
				new LodestoneTrackerComponent(Optional.of(GlobalPos.create(world.getRegistryKey(), pos)), false)
		);
	}
}
