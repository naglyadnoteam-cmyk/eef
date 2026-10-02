package com.naglyadno.nedra.hazard;

import com.naglyadno.nedra.block.ModBlocks;
import com.naglyadno.nedra.config.NedraConfig;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;

import java.util.Optional;

/**
 * Магнетитовая руда сбивает обычный (не привязанный к маяку) компас: стрелка
 * начинает "сходить с ума", как у ванильного компаса с потерянным лодстоуном.
 * Мы просто подсовываем компасу фиктивную цель-лодстоун в заведомо несуществующей
 * точке - ванильная отрисовка сама превращает это в хаотичное дрожание стрелки.
 */
public class MagnetiteInterferenceManager {

	/** Опознавательная "фальшивая" точка - компасы, привязанные сюда, созданы этим менеджером. */
	private static final BlockPos FAKE_TARGET_POS = new BlockPos(0, -2031, 0);

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
		for (ServerWorld world : server.getWorlds()) {
			if (world.getRegistryKey() != World.OVERWORLD) {
				continue;
			}
			for (ServerPlayerEntity player : world.getPlayers()) {
				boolean nearMagnetite = isNearMagnetite(world, player);
				updateHand(player, Hand.MAIN_HAND, nearMagnetite, world);
				updateHand(player, Hand.OFF_HAND, nearMagnetite, world);
			}
		}
	}

	private boolean isNearMagnetite(ServerWorld world, ServerPlayerEntity player) {
		BlockPos base = player.getBlockPos();
		int r = (int) Math.ceil(config.magnetiteInterferenceRadius);
		for (BlockPos pos : BlockPos.iterate(base.add(-r, -r, -r), base.add(r, r, r))) {
			if (!world.getBlockState(pos).isOf(ModBlocks.MAGNETITE_ORE)) {
				continue;
			}
			double dist = Math.sqrt(pos.getSquaredDistance(player.getEntityPos()));
			if (dist <= config.magnetiteInterferenceRadius) {
				return true;
			}
		}
		return false;
	}

	private void updateHand(ServerPlayerEntity player, Hand hand, boolean nearMagnetite, ServerWorld world) {
		ItemStack stack = player.getStackInHand(hand);
		if (!stack.isOf(Items.COMPASS)) {
			return;
		}
		LodestoneTrackerComponent current = stack.get(DataComponentTypes.LODESTONE_TRACKER);
		boolean isOurFake = current != null && current.target()
				.map(pos -> pos.pos().equals(FAKE_TARGET_POS))
				.orElse(false);

		if (nearMagnetite) {
			if (current == null) {
				GlobalPos fakeTarget = GlobalPos.create(world.getRegistryKey(), FAKE_TARGET_POS);
				stack.set(DataComponentTypes.LODESTONE_TRACKER,
						new LodestoneTrackerComponent(Optional.of(fakeTarget), true));
			}
		} else if (isOurFake) {
			stack.remove(DataComponentTypes.LODESTONE_TRACKER);
		}
	}
}
