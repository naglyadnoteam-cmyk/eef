package com.naglyadno.speedhunt.network;

import com.naglyadno.speedhunt.SpeedHuntMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Клиент -> сервер: запрос "начать матч" / "остановить матч" по кнопке из
 * меню Speedhunt (альтернатива командам /speedhunt start|stop).
 */
public record RequestActionPayload(boolean start) implements CustomPayload {

	public static final CustomPayload.Id<RequestActionPayload> ID =
			new CustomPayload.Id<>(Identifier.of(SpeedHuntMod.MOD_ID, "request_action"));

	public static final PacketCodec<RegistryByteBuf, RequestActionPayload> CODEC =
			PacketCodec.of(RequestActionPayload::write, RequestActionPayload::read);

	private static void write(RequestActionPayload payload, RegistryByteBuf buf) {
		buf.writeBoolean(payload.start);
	}

	private static RequestActionPayload read(RegistryByteBuf buf) {
		return new RequestActionPayload(buf.readBoolean());
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
