package com.naglyadno.speedhunt.network;

import com.naglyadno.speedhunt.SpeedHuntMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Сервер -> клиент: короткое предупреждение вверху экрана (например, "Спидраннер вошёл в Нижний мир"). */
public record BannerPayload(String text) implements CustomPayload {

	public static final CustomPayload.Id<BannerPayload> ID =
			new CustomPayload.Id<>(Identifier.of(SpeedHuntMod.MOD_ID, "banner"));

	public static final PacketCodec<RegistryByteBuf, BannerPayload> CODEC =
			PacketCodec.of(BannerPayload::write, BannerPayload::read);

	private static void write(BannerPayload payload, RegistryByteBuf buf) {
		buf.writeString(payload.text);
	}

	private static BannerPayload read(RegistryByteBuf buf) {
		return new BannerPayload(buf.readString());
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
