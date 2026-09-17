package com.naglyadno.speedhunt.network;

import com.naglyadno.speedhunt.SpeedHuntMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Клиент -> сервер: "выдай мне компас на противоположную роль". */
public record RequestCompassPayload() implements CustomPayload {

	public static final CustomPayload.Id<RequestCompassPayload> ID =
			new CustomPayload.Id<>(Identifier.of(SpeedHuntMod.MOD_ID, "request_compass"));

	public static final PacketCodec<RegistryByteBuf, RequestCompassPayload> CODEC =
			PacketCodec.unit(new RequestCompassPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
