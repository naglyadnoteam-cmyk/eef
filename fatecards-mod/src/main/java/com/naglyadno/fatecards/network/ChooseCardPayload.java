package com.naglyadno.fatecards.network;

import com.naglyadno.fatecards.FateCardsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Клиент -> сервер: "я выбираю эту карту" (по id одной из двух предложенных). */
public record ChooseCardPayload(String cardId) implements CustomPayload {

	public static final CustomPayload.Id<ChooseCardPayload> ID =
			new CustomPayload.Id<>(Identifier.of(FateCardsMod.MOD_ID, "choose_card"));

	public static final PacketCodec<RegistryByteBuf, ChooseCardPayload> CODEC =
			PacketCodec.of(ChooseCardPayload::write, ChooseCardPayload::read);

	private static void write(ChooseCardPayload payload, RegistryByteBuf buf) {
		buf.writeString(payload.cardId);
	}

	private static ChooseCardPayload read(RegistryByteBuf buf) {
		return new ChooseCardPayload(buf.readString());
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
