package com.naglyadno.nedra.network;

import com.naglyadno.nedra.NedraMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Текущее давление игрока для HUD: эффективное (с учётом защиты) и "сырое" (без защиты),
 * суммарная защита в процентах, ярус 0..5 и признак "давление действует" (только в Overworld).
 */
public record PressurePayload(float effective, float raw, int protection, int tier, boolean active) implements CustomPayload {

	public static final CustomPayload.Id<PressurePayload> ID =
			new CustomPayload.Id<>(Identifier.of(NedraMod.MOD_ID, "pressure_state"));

	public static final PacketCodec<RegistryByteBuf, PressurePayload> CODEC =
			PacketCodec.of(PressurePayload::write, PressurePayload::read);

	private static void write(PressurePayload p, RegistryByteBuf buf) {
		buf.writeFloat(p.effective);
		buf.writeFloat(p.raw);
		buf.writeByte(p.protection);
		buf.writeByte(p.tier);
		buf.writeBoolean(p.active);
	}

	private static PressurePayload read(RegistryByteBuf buf) {
		return new PressurePayload(buf.readFloat(), buf.readFloat(), buf.readByte(), buf.readByte(), buf.readBoolean());
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
