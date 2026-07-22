package com.tacticalpvp.mod.network;

import com.tacticalpvp.mod.hud.ClientHudState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Сервер -> Клієнт: синхронізація рахунку команд і часу, що лишився у матчі. */
public class MatchHudSyncPacket {
    public final int redScore;
    public final int blueScore;
    public final int remainingSeconds;

    public MatchHudSyncPacket(int redScore, int blueScore, int remainingSeconds) {
        this.redScore = redScore;
        this.blueScore = blueScore;
        this.remainingSeconds = remainingSeconds;
    }

    public static void encode(MatchHudSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.redScore);
        buf.writeVarInt(msg.blueScore);
        buf.writeVarInt(msg.remainingSeconds);
    }

    public static MatchHudSyncPacket decode(FriendlyByteBuf buf) {
        return new MatchHudSyncPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(MatchHudSyncPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> ClientHudState.setMatchInfo(msg.redScore, msg.blueScore, msg.remainingSeconds));
        ctx.setPacketHandled(true);
    }
}
