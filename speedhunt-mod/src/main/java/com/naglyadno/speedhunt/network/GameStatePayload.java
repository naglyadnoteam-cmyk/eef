package com.naglyadno.speedhunt.network;

import com.naglyadno.speedhunt.SpeedHuntMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Состояние матча, которое сервер шлёт клиенту каждые несколько тиков,
 * чтобы отрисовать HUD в правом нижнем углу (роль, таймер, трекер).
 */
public record GameStatePayload(
		String role,
		String gameState,
		int countdownSeconds,
		int elapsedSeconds,
		boolean trackerActive,
		float trackerYaw,
		float trackerDistance,
		String lastResultMessage
) implements CustomPayload {

	public static final CustomPayload.Id<GameStatePayload> ID =
			new CustomPayload.Id<>(Identifier.of(SpeedHuntMod.MOD_ID, "game_state"));

	public static final PacketCodec<RegistryByteBuf, GameStatePayload> CODEC =
			PacketCodec.of(GameStatePayload::write, GameStatePayload::read);

	private static void write(GameStatePayload payload, RegistryByteBuf buf) {
		buf.writeString(payload.role);
		buf.writeString(payload.gameState);
		buf.writeInt(payload.countdownSeconds);
		buf.writeInt(payload.elapsedSeconds);
		buf.writeBoolean(payload.trackerActive);
		buf.writeFloat(payload.trackerYaw);
		buf.writeFloat(payload.trackerDistance);
		buf.writeString(payload.lastResultMessage);
	}

	private static GameStatePayload read(RegistryByteBuf buf) {
		return new GameStatePayload(
				buf.readString(),
				buf.readString(),
				buf.readInt(),
				buf.readInt(),
				buf.readBoolean(),
				buf.readFloat(),
				buf.readFloat(),
				buf.readString()
		);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
