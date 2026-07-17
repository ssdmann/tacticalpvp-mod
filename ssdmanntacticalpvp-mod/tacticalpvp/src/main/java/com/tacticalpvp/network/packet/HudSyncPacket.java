package com.tacticalpvp.network.packet;

import com.tacticalpvp.client.ClientHudState;
import com.tacticalpvp.match.MatchState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.function.Supplier;

/**
 * Sent server -> client to keep the top-center HUD (scores + remaining time) synced.
 * Sent on match start/stop/end and periodically (every ~0.5s) while running.
 */
public class HudSyncPacket {

    private final int redScore;
    private final int blueScore;
    private final int remainingTicks;
    private final boolean infiniteTime;
    private final MatchState state;

    public HudSyncPacket(int redScore, int blueScore, int remainingTicks, boolean infiniteTime, MatchState state) {
        this.redScore = redScore;
        this.blueScore = blueScore;
        this.remainingTicks = remainingTicks;
        this.infiniteTime = infiniteTime;
        this.state = state;
    }

    public static void encode(HudSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.redScore);
        buf.writeInt(msg.blueScore);
        buf.writeInt(msg.remainingTicks);
        buf.writeBoolean(msg.infiniteTime);
        buf.writeEnum(msg.state);
    }

    public static HudSyncPacket decode(FriendlyByteBuf buf) {
        int red = buf.readInt();
        int blue = buf.readInt();
        int ticks = buf.readInt();
        boolean infinite = buf.readBoolean();
        MatchState state = buf.readEnum(MatchState.class);
        return new HudSyncPacket(red, blue, ticks, infinite, state);
    }

    public static void handle(HudSyncPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> handleClient(msg));
        ctx.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(HudSyncPacket msg) {
        ClientHudState.update(msg.redScore, msg.blueScore, msg.remainingTicks, msg.infiniteTime, msg.state);
    }
}
