package com.naglyadno.nedra.hazard;

import com.naglyadno.nedra.block.ModBlocks;
import com.naglyadno.nedra.component.ModComponents;
import com.naglyadno.nedra.config.NedraConfig;
import com.naglyadno.nedra.sound.ModSounds;
import com.naglyadno.nedra.util.BlockScanner;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;

import java.util.Optional;

/**
 * Магнетит сбивает обычный компас в руке: компасу выдаётся "отслеживаемый" трекер лодстоуна
 * без цели - ровно то состояние, в котором ванильная стрелка хаотично вращается. Подменённые
 * компасы помечаются компонентом nedra:magnetized; как только игрок отходит от магнетита или
 * убирает компас из руки, подмена снимается со всех помеченных компасов в его инвентаре.
 * Настоящие лодстоун-компасы (без метки) не трогаются никогда.
 */
public class MagnetiteInterferenceManager {

	private static final LodestoneTrackerComponent SPINNING = new LodestoneTrackerComponent(Optional.empty(), true);

	private final NedraConfig config;
	private long tickCounter;

	public MagnetiteInterferenceManager(NedraConfig config) {
		this.config = config;
	}

	public void tick(MinecraftServer server) {
		tickCounter++;
		if (tickCounter % 10 != 0) {
			return;
		}
		ServerWorld overworld = server.getOverworld();
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			boolean inOverworld = player.getEntityWorld() == overworld;
			boolean holdsCompass = player.getMainHandStack().isOf(Items.COMPASS) || player.getOffHandStack().isOf(Items.COMPASS);
			boolean near = inOverworld && holdsCompass && BlockScanner.findNearest(overworld, player.getBlockPos(),
					config.magnetiteInterferenceRadius, ModBlocks.MAGNETITE_ORE) != null;
			update(player, near);
		}
	}

	private void update(ServerPlayerEntity player, boolean near) {
		PlayerInventory inventory = player.getInventory();
		boolean magnetizedNow = false;
		for (int i = 0; i < inventory.size(); i++) {
			ItemStack stack = inventory.getStack(i);
			if (!stack.isOf(Items.COMPASS)) {
				continue;
			}
			boolean held = stack == player.getMainHandStack() || stack == player.getOffHandStack();
			boolean ours = Boolean.TRUE.equals(stack.get(ModComponents.MAGNETIZED));
			if (near && held) {
				if (!ours && stack.get(DataComponentTypes.LODESTONE_TRACKER) == null) {
					stack.set(DataComponentTypes.LODESTONE_TRACKER, SPINNING);
					stack.set(ModComponents.MAGNETIZED, true);
					magnetizedNow = true;
				}
			} else if (ours) {
				stack.remove(DataComponentTypes.LODESTONE_TRACKER);
				stack.remove(ModComponents.MAGNETIZED);
			}
		}
		if (magnetizedNow) {
			player.getEntityWorld().playSound(null, player.getBlockPos(), ModSounds.MAGNETITE_BUZZ,
					SoundCategory.PLAYERS, 0.6f, 1.0f);
		}
	}
}
