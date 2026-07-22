package com.tacticalpvp.mod.network;

import com.tacticalpvp.mod.hud.ClientHudState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер -> Клієнт: показати ненав'язливий індикатор в Action Bar
 * "Захоплення точки N: XX%". active=false ховає індикатор.
 */
public class CaptureProgressPacket {
    public final boolean active;
    public final int pointIndex;
    public final int percent;

    public CaptureProgressPacket(boolean active, int pointIndex, int percent) {
        this.active = active;
        this.pointIndex = pointIndex;
        this.percent = percent;
    }

    public static void encode(CaptureProgressPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.active);
        buf.writeVarInt(msg.pointIndex);
        buf.writeVarInt(msg.percent);
    }

    public static CaptureProgressPacket decode(FriendlyByteBuf buf) {
        return new CaptureProgressPacket(buf.readBoolean(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(CaptureProgressPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            if (net.minecraftforge.fml.loading.FMLEnvironment.dist == Dist.CLIENT) {
                ClientHudState.setCaptureProgress(msg.active, msg.pointIndex, msg.percent);
            }
        });
        ctx.setPacketHandled(true);
    }
}
