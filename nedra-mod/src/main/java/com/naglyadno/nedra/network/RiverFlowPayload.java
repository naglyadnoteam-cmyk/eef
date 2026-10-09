package com.naglyadno.nedra.network;

import com.naglyadno.nedra.NedraMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Течение подземной реки под игроком: направление (единичный вектор x, z) и признак "игрок в реке". */
public record RiverFlowPayload(float dx, float dz, boolean active) implements CustomPayload {

	public static final CustomPayload.Id<RiverFlowPayload> ID = new CustomPayload.Id<>(Identifier.of(NedraMod.MOD_ID, "river_flow"));

	public static final PacketCodec<RegistryByteBuf, RiverFlowPayload> CODEC = PacketCodec.of(RiverFlowPayload::write, RiverFlowPayload::read);

	private static void write(RiverFlowPayload p, RegistryByteBuf buf) {
		buf.writeFloat(p.dx);
		buf.writeFloat(p.dz);
		buf.writeBoolean(p.active);
	}

	private static RiverFlowPayload read(RegistryByteBuf buf) {
		return new RiverFlowPayload(buf.readFloat(), buf.readFloat(), buf.readBoolean());
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
