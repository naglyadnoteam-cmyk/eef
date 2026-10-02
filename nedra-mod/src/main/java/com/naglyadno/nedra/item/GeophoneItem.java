package com.naglyadno.nedra.item;

import com.naglyadno.nedra.NedraMod;
import com.naglyadno.nedra.block.ModBlocks;
import com.naglyadno.nedra.sound.ModSounds;
import com.naglyadno.nedra.util.BlockScanner;
import com.naglyadno.nedra.util.ServerScheduler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Геофон: посылает в породу звуковой импульс и "слушает" ответ. Сообщает тип, расстояние,
 * направление (по горизонтали и по вертикали) до ближайшей эхо-руды или магнетита, а ответное
 * эхо через паузу (тем дольше, чем дальше руда) приходит именно с той стороны, где она лежит.
 */
public class GeophoneItem extends Item {

	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

	public GeophoneItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!(world instanceof ServerWorld serverWorld)) {
			return ActionResult.SUCCESS;
		}
		int cooldown = NedraMod.config().geophoneCooldownTicks;
		user.getItemCooldownManager().set(stack, cooldown);
		stack.damage(1, user, hand);

		BlockPos origin = BlockPos.ofFloored(user.getEyePos());
		serverWorld.playSound(null, origin, ModSounds.GEOPHONE_PING, SoundCategory.PLAYERS, 0.9f, 1.0f);

		int radius = NedraMod.config().geophoneRadius;
		BlockPos echo = BlockScanner.findNearest(serverWorld, origin, radius, ModBlocks.ECHO_ORE);
		BlockPos magnet = BlockScanner.findNearest(serverWorld, origin, radius, ModBlocks.MAGNETITE_ORE);

		BlockPos target;
		boolean isEcho;
		if (echo != null && (magnet == null || echo.getSquaredDistance(origin) <= magnet.getSquaredDistance(origin) * 1.5)) {
			target = echo;
			isEcho = true;
		} else {
			target = magnet;
			isEcho = false;
		}

		if (target == null) {
			user.sendMessage(Text.translatable("message.nedra.geophone.silence").formatted(Formatting.GRAY), true);
			return ActionResult.SUCCESS;
		}

		Vec3d eye = user.getEyePos();
		Vec3d toTarget = Vec3d.ofCenter(target).subtract(eye);
		double distance = toTarget.length();
		user.sendMessage(describe(user, toTarget, distance, isEcho), true);

		// Видимый импульс в сторону находки
		Vec3d dir = toTarget.normalize();
		for (int i = 1; i <= 4; i++) {
			Vec3d p = eye.add(dir.multiply(i * 0.8));
			serverWorld.spawnParticles(ParticleTypes.SCULK_CHARGE_POP, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
		}

		// Ответное эхо: виртуальный источник на линии к руде, но не дальше 10 блоков - чтобы его
		// было слышно, и слышно именно с нужной стороны.
		Vec3d echoPos = eye.add(dir.multiply(Math.min(distance, 10.0)));
		float pitch = (float) MathHelper.clamp(1.6 - distance / 40.0, 0.7, 1.6);
		int delay = 6 + (int) (distance * 0.6);
		ServerScheduler.schedule(delay, () -> {
			if (!user.isRemoved()) {
				serverWorld.playSound(null, echoPos.x, echoPos.y, echoPos.z, ModSounds.GEOPHONE_RETURN,
						SoundCategory.PLAYERS, isEcho ? 1.0f : 0.7f, pitch);
			}
		});
		return ActionResult.SUCCESS;
	}

	private static Text describe(PlayerEntity user, Vec3d toTarget, double distance, boolean isEcho) {
		double angleToTarget = Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
		float relative = MathHelper.wrapDegrees((float) angleToTarget - user.getYaw());
		String arrow = ARROWS[Math.floorMod(Math.round(relative / 45f), 8)];

		String vertical;
		if (toTarget.y > 2.5) {
			vertical = "▲";
		} else if (toTarget.y < -2.5) {
			vertical = "▼";
		} else {
			vertical = "●";
		}

		int strength = distance < 8 ? 4 : distance < 16 ? 3 : distance < 26 ? 2 : 1;
		String bars = "▮".repeat(strength) + "▯".repeat(4 - strength);

		MutableText kind = Text.translatable(isEcho ? "message.nedra.geophone.echo" : "message.nedra.geophone.magnetite")
				.formatted(isEcho ? Formatting.AQUA : Formatting.GRAY);
		return Text.empty()
				.append(Text.literal(bars + " ").formatted(isEcho ? Formatting.DARK_AQUA : Formatting.DARK_GRAY))
				.append(kind)
				.append(Text.literal("  " + arrow + " " + vertical + "  ").formatted(Formatting.WHITE))
				.append(Text.translatable("message.nedra.geophone.distance", Math.round(distance)).formatted(Formatting.GRAY));
	}
}
