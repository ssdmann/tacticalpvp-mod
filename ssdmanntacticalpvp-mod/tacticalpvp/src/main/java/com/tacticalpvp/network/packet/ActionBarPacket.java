package com.tacticalpvp.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: display a short-lived action bar message (capture progress, success, lobby countdown). */
public class ActionBarPacket {

    private final Component message;

    public ActionBarPacket(Component message) {
        this.message = message;
    }

    public static void encode(ActionBarPacket msg, net.minecraft.network.FriendlyByteBuf buf) {
        buf.writeComponent(msg.message);
    }

    public static ActionBarPacket decode(net.minecraft.network.FriendlyByteBuf buf) {
        return new ActionBarPacket(buf.readComponent());
    }

    public static void handle(ActionBarPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> handleClient(msg));
        ctx.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(ActionBarPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(msg.message, true);
        }
    }
}
