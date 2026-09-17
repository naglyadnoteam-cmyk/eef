package com.naglyadno.speedhunt.network;

import com.naglyadno.speedhunt.SpeedHuntMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Клиент -> сервер: новый стартовый лут для роли ("HUNTER" или "SPEEDRUNNER"), задан из редактора лута. */
public record SetLoadoutPayload(String role, List<String> itemIds, List<Integer> counts) implements CustomPayload {

	public static final CustomPayload.Id<SetLoadoutPayload> ID =
			new CustomPayload.Id<>(Identifier.of(SpeedHuntMod.MOD_ID, "set_loadout"));

	public static final PacketCodec<RegistryByteBuf, SetLoadoutPayload> CODEC =
			PacketCodec.of(SetLoadoutPayload::write, SetLoadoutPayload::read);

	private static void write(SetLoadoutPayload payload, RegistryByteBuf buf) {
		buf.writeString(payload.role);
		buf.writeInt(payload.itemIds.size());
		for (int i = 0; i < payload.itemIds.size(); i++) {
			buf.writeString(payload.itemIds.get(i));
			buf.writeInt(payload.counts.get(i));
		}
	}

	private static SetLoadoutPayload read(RegistryByteBuf buf) {
		String role = buf.readString();
		int size = buf.readInt();
		List<String> ids = new ArrayList<>(size);
		List<Integer> counts = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			ids.add(buf.readString());
			counts.add(buf.readInt());
		}
		return new SetLoadoutPayload(role, ids, counts);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
