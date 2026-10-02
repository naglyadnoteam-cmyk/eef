package com.naglyadno.nedra.network;

import com.naglyadno.nedra.NedraMod;
import com.naglyadno.nedra.config.NedraConfig;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Значения серверного конфига, которые нужны клиенту для честных подсказок к предметам. */
public record ConfigSyncPayload(int helmetLight, int helmetReinforced, int helmetDeepsuit,
		int tabletReduction, int tabletSeconds, int geophoneRadius) implements CustomPayload {

	public static final CustomPayload.Id<ConfigSyncPayload> ID =
			new CustomPayload.Id<>(Identifier.of(NedraMod.MOD_ID, "config_sync"));

	public static final PacketCodec<RegistryByteBuf, ConfigSyncPayload> CODEC =
			PacketCodec.of(ConfigSyncPayload::write, ConfigSyncPayload::read);

	public static ConfigSyncPayload of(NedraConfig config) {
		return new ConfigSyncPayload(config.helmetLightReductionPercent, config.helmetReinforcedReductionPercent,
				config.helmetDeepsuitReductionPercent, config.tabletReductionPercent,
				config.tabletDurationTicks / 20, config.geophoneRadius);
	}

	private static void write(ConfigSyncPayload p, RegistryByteBuf buf) {
		buf.writeVarInt(p.helmetLight);
		buf.writeVarInt(p.helmetReinforced);
		buf.writeVarInt(p.helmetDeepsuit);
		buf.writeVarInt(p.tabletReduction);
		buf.writeVarInt(p.tabletSeconds);
		buf.writeVarInt(p.geophoneRadius);
	}

	private static ConfigSyncPayload read(RegistryByteBuf buf) {
		return new ConfigSyncPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
				buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
