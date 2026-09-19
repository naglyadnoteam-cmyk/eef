package com.naglyadno.fatecards.network;

import com.naglyadno.fatecards.FateCardsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Клиент -> сервер: запрос старт/стоп режима (кнопка в меню, клавиша N). */
public record RequestActionPayload(boolean start) implements CustomPayload {

	public static final CustomPayload.Id<RequestActionPayload> ID =
			new CustomPayload.Id<>(Identifier.of(FateCardsMod.MOD_ID, "request_action"));

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
