package com.naglyadno.nedra.item;

import com.naglyadno.nedra.NedraMod;
import com.naglyadno.nedra.block.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Геофон: по ПКМ ищет ближайшую эхо-руду/магнетит в радиусе и подсказывает направление и расстояние. */
public class GeophoneItem extends Item {

	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

	public GeophoneItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world.isClient || !(world instanceof ServerWorld serverWorld)) {
			return TypedActionResult.success(stack, true);
		}
		world.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.8f, 1.4f);

		int radius = (int) NedraMod.config().geophoneHearRadius;
		BlockPos origin = user.getBlockPos();
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.iterate(origin.add(-radius, -radius, -radius), origin.add(radius, radius, radius))) {
			Block block = serverWorld.getBlockState(pos).getBlock();
			if (block == ModBlocks.ECHO_ORE || block == ModBlocks.MAGNETITE_ORE) {
				double dist = pos.getSquaredDistance(origin);
				if (dist < bestDist) {
					bestDist = dist;
					best = pos.toImmutable();
				}
			}
		}

		if (best == null) {
			user.sendMessage(Text.literal("Тишина... поблизости ничего не слышно.").formatted(Formatting.GRAY), true);
		} else {
			double dx = best.getX() + 0.5 - user.getX();
			double dz = best.getZ() + 0.5 - user.getZ();
			double angleToTarget = Math.toDegrees(Math.atan2(-dx, dz));
			double relative = MathHelper.wrapDegrees(angleToTarget - user.getYaw());
			String arrow = arrowFor((float) relative);
			int distance = (int) Math.round(Math.sqrt(bestDist));
			user.sendMessage(Text.literal(arrow + " Источник звука примерно в " + distance + " блоках")
					.formatted(Formatting.AQUA), true);
		}

		return TypedActionResult.success(stack, false);
	}

	private static String arrowFor(float relativeYaw) {
		float normalized = relativeYaw % 360f;
		if (normalized < 0) {
			normalized += 360f;
		}
		int index = Math.round(normalized / 45f) % 8;
		return ARROWS[index];
	}
}
