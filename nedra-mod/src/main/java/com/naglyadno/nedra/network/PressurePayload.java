package com.naglyadno.nedra.network;

import com.naglyadno.nedra.NedraMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Сервер шлёт клиенту его текущее давление (0..100) и ярус (0..5) для отрисовки HUD. */
public record PressurePayload(double pressure, int tier) implements CustomPayload {

	public static final CustomPayload.Id<PressurePayload> ID =
			new CustomPayload.Id<>(Identifier.of(NedraMod.MOD_ID, "pressure_state"));

	public static final PacketCodec<RegistryByteBuf, PressurePayload> CODEC =
			PacketCodec.of(PressurePayload::write, PressurePayload::read);

	private static void write(PressurePayload p, RegistryByteBuf buf) {
		buf.writeDouble(p.pressure);
		buf.writeInt(p.tier);
	}

	private static PressurePayload read(RegistryByteBuf buf) {
		return new PressurePayload(buf.readDouble(), buf.readInt());
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
