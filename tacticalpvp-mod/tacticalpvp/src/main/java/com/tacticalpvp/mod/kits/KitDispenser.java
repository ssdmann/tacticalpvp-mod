package com.tacticalpvp.mod.kits;

import com.tacticalpvp.mod.util.Team;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Random;

/**
 * Видає гравцю предмети кіта, що визначені у ClassKitConfig для його команди/класу.
 * Викликається ОДИН РАЗ при виборі класу (бо keepInventory увімкнено глобально,
 * тож повторна видача при відродженні не потрібна).
 */
public class KitDispenser {

    private static final Random RANDOM = new Random();

    public static void giveKit(ServerPlayer player, Team team, PlayerClass cls) {
        KitConfigLoader.ClassKitConfig cfg = KitConfigLoader.get(team, cls);
        for (KitEntry entry : cfg.items) {
            if (entry.isRandom) {
                KitEntry.WeightedOption chosen = entry.rollRandom(RANDOM);
                giveItem(player, chosen.item, chosen.count);
                if (chosen.ammoItem != null && chosen.ammoCount > 0) {
                    giveItem(player, chosen.ammoItem, chosen.ammoCount);
                }
            } else {
                giveItem(player, entry.simpleItem, entry.simpleCount);
            }
        }
    }

    private static void giveItem(ServerPlayer player, String itemId, int count) {
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(itemId));
        if (item == null) return;
        int remaining = count;
        int maxStack = new ItemStack(item).getMaxStackSize();
        while (remaining > 0) {
            int stackSize = Math.min(maxStack, remaining);
            player.getInventory().add(new ItemStack(item, stackSize));
            remaining -= stackSize;
        }
    }
}
