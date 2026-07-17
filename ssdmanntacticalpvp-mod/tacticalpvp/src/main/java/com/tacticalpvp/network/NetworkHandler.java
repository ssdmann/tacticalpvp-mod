package com.tacticalpvp.network;

import com.tacticalpvp.TacticalPvpMod;
import com.tacticalpvp.network.packet.ActionBarPacket;
import com.tacticalpvp.network.packet.HudSyncPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class NetworkHandler {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TacticalPvpMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int id = 0;

    public static void register() {
        CHANNEL.registerMessage(id++, HudSyncPacket.class,
                HudSyncPacket::encode, HudSyncPacket::decode, HudSyncPacket::handle);

        CHANNEL.registerMessage(id++, ActionBarPacket.class,
                ActionBarPacket::encode, ActionBarPacket::decode, ActionBarPacket::handle);
    }
}
