package com.tacticalpvp.mod.network;

import com.tacticalpvp.mod.TacticalPvpMod;
import com.tacticalpvp.mod.item.EnderEyeKitItem;
import com.tacticalpvp.mod.kits.ClassLimitManager;
import com.tacticalpvp.mod.kits.KitDispenser;
import com.tacticalpvp.mod.kits.PlayerClass;
import com.tacticalpvp.mod.util.PlayerTeamTracker;
import com.tacticalpvp.mod.util.Team;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Клієнт -> Сервер: обробка вибору класу у GUI.
 * Перевіряє ліміт класу, видає кіт та ВИДАЛЯЄ Око Ендера з інвентарю.
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

            // 1. Перевірка ліміту класу для команди
            if (limitManager != null && !limitManager.hasFreeSlot(team, msg.requestedClass, teamSize)) {
                player.sendSystemMessage(Component.translatable("message.tacticalpvp.class_limit_reached", msg.requestedClass.uaName));
                return;
            }

            // 2. Видалення Ока Ендера (перевірка ванільного або кастомного)
            boolean removed = false;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (isEyeItem(stack)) {
                    stack.shrink(1);
                    removed = true;
                    break;
                }
            }

            // Якщо прямо в циклі не зменшилось, робимо зачистку 1 штуки
            if (!removed) {
                int count = player.getInventory().clearOrCountMatchingItems(SelectClassPacket::isEyeItem, 1, player.getInventory());
                if (count <= 0) {
                    return; // Немає Ока — кіт не видаємо!
                }
            }

            // 3. Призначаємо клас та видаємо кіт
            if (limitManager != null) {
                limitManager.assign(player.getUUID(), team, msg.requestedClass);
            }
            
            KitDispenser.giveKit(player, team, msg.requestedClass);
            player.sendSystemMessage(Component.translatable("message.tacticalpvp.class_selected", msg.requestedClass.uaName));
        });
        ctx.setPacketHandled(true);
    }

    /** Метод перевіряє, чи є предмет Оком Ендера (ванільним або кастомним з моду) */
    private static boolean isEyeItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(Items.ENDER_EYE)) return true;
        if (stack.getItem() instanceof EnderEyeKitItem) return true;
        
        String itemId = stack.getItem().toString();
        return itemId.contains("ender_eye");
    }
}