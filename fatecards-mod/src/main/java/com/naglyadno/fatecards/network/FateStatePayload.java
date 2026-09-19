package com.naglyadno.fatecards.network;

import com.naglyadno.fatecards.FateCardsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Периодическое состояние, которое сервер шлёт каждому игроку: идёт ли режим,
 * сколько до следующего раунда, и (только этому игроку) его текущий выбор
 * из двух карт, если раунд как раз начался.
 */
public record FateStatePayload(
		String gameState,
		int secondsToNextRound,
		boolean offerPending,
		String targetName,
		String card1Id,
		String card1Name,
		String card1Description,
		String card1Category,
		String card2Id,
		String card2Name,
		String card2Description,
		String card2Category,
		int offerSecondsLeft,
		String lastAnnouncement
) implements CustomPayload {

	public static final CustomPayload.Id<FateStatePayload> ID =
			new CustomPayload.Id<>(Identifier.of(FateCardsMod.MOD_ID, "fate_state"));

	public static final PacketCodec<RegistryByteBuf, FateStatePayload> CODEC =
			PacketCodec.of(FateStatePayload::write, FateStatePayload::read);

	private static void write(FateStatePayload p, RegistryByteBuf buf) {
		buf.writeString(p.gameState);
		buf.writeInt(p.secondsToNextRound);
		buf.writeBoolean(p.offerPending);
		buf.writeString(p.targetName);
		buf.writeString(p.card1Id);
		buf.writeString(p.card1Name);
		buf.writeString(p.card1Description);
		buf.writeString(p.card1Category);
		buf.writeString(p.card2Id);
		buf.writeString(p.card2Name);
		buf.writeString(p.card2Description);
		buf.writeString(p.card2Category);
		buf.writeInt(p.offerSecondsLeft);
		buf.writeString(p.lastAnnouncement);
	}

	private static FateStatePayload read(RegistryByteBuf buf) {
		return new FateStatePayload(
				buf.readString(),
				buf.readInt(),
				buf.readBoolean(),
				buf.readString(),
				buf.readString(),
				buf.readString(),
				buf.readString(),
				buf.readString(),
				buf.readString(),
				buf.readString(),
				buf.readString(),
				buf.readString(),
				buf.readInt(),
				buf.readString()
		);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
