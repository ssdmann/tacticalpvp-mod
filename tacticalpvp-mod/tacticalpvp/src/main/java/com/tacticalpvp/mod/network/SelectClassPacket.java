package com.tacticalpvp.mod.network;

import com.tacticalpvp.mod.TacticalPvpMod;
import com.tacticalpvp.mod.kits.ClassLimitManager;
import com.tacticalpvp.mod.kits.KitDispenser;
import com.tacticalpvp.mod.kits.PlayerClass;
import com.tacticalpvp.mod.util.PlayerTeamTracker;
import com.tacticalpvp.mod.util.Team;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Клієнт -> Сервер: гравець обрав клас у GUI.
 * Сервер перевіряє відсотковий ліміт класу для команди гравця; якщо ліміт
 * вичерпано — блокує вибір і надсилає попередження, інакше видає кіт один раз
 * і забирає Око Ендера з інвентаря.
 */
public class SelectClassPacket {

    private final PlayerClass requestedClass;

    public SelectClassPacket(PlayerClass cls) {
        this.requestedClass = cls;
    }

    public static void encode(SelectClassPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.requestedClass);
    }

    public static SelectClassPacket decode(FriendlyByteBuf buf) {
        return new SelectClassPacket(buf.readEnum(PlayerClass.class));
    }

    public static void handle(SelectClassPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            Team team = PlayerTeamTracker.get(player.getUUID());
            if (team == Team.NEUTRAL) {
                player.sendSystemMessage(Component.translatable("message.tacticalpvp.no_team"));
                return;
            }

            int teamSize = PlayerTeamTracker.countTeam(team);
            ClassLimitManager limitManager = TacticalPvpMod.CLASS_LIMIT_MANAGER;

            if (!limitManager.hasFreeSlot(team, msg.requestedClass, teamSize)) {
                player.sendSystemMessage(Component.translatable("message.tacticalpvp.class_limit_reached", msg.requestedClass.uaName));
                return; // Гравець лишається у GUI (на клієнті ми просто закрили екран - можна за потреби
                        // повторно відкрити; головне що клас НЕ призначено і кіт НЕ видано).
            }

            limitManager.assign(player.getUUID(), team, msg.requestedClass);
            KitDispenser.giveKit(player, team, msg.requestedClass);

            // Забираємо Око Ендера (предмет вибору класу) з інвентаря - вибір одноразовий.
            player.getInventory().clearOrCountMatchingItems(
                    stack -> stack.getItem() == com.tacticalpvp.mod.item.ModItems.ENDER_EYE_KIT.get(), -1, player.getInventory());

            player.sendSystemMessage(Component.translatable("message.tacticalpvp.class_selected", msg.requestedClass.uaName));
        });
        ctx.setPacketHandled(true);
    }
}
