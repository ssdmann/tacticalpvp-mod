package com.tacticalpvp.mod.kits;

import com.mojang.logging.LogUtils;
import com.tacticalpvp.mod.util.Team;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

import java.util.Random;

/**
 * Видає гравцю предмети кіта, що визначені у ClassKitConfig для його команди/класу.
 */
public class KitDispenser {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Random RANDOM = new Random();

    public static void giveKit(ServerPlayer player, Team team, PlayerClass cls) {
        KitConfigLoader.ClassKitConfig cfg = KitConfigLoader.get(team, cls);
        if (cfg == null || cfg.items == null || cfg.items.isEmpty()) {
            LOGGER.warn("Кіт порожній або не завантажений для команди: " + team + ", класу: " + cls);
            return;
        }

        for (KitEntry entry : cfg.items) {
            if (entry.isRandom) {
                KitEntry.WeightedOption chosen = entry.rollRandom(RANDOM);
                if (chosen != null) {
                    // Видаємо випадкову зброю з її NBT-тегами
                    giveItem(player, chosen.item, chosen.count, chosen.nbt);
                    
                    // Видаємо відповідні патрони до неї з їхніми NBT-тегами
                    if (chosen.ammoItem != null && chosen.ammoCount > 0) {
                        giveItem(player, chosen.ammoItem, chosen.ammoCount, chosen.ammoNbt);
                    }
                }
            } else {
                // Видаємо звичайний предмет із його NBT (наприклад, лопату з Unbreakable)
                giveItem(player, entry.simpleItem, entry.simpleCount, entry.simpleNbt);
            }
        }

        player.containerMenu.broadcastChanges();
        player.inventoryMenu.broadcastChanges();
    }

    private static void giveItem(ServerPlayer player, String itemId, int count, CompoundTag nbt) {
        if (itemId == null || itemId.isEmpty() || count <= 0) return;

        ResourceLocation loc = ResourceLocation.tryParse(itemId);
        if (loc == null) {
            LOGGER.error("Некоректний синтаксис ID предмета: " + itemId);
            return;
        }

        Item item = BuiltInRegistries.ITEM.get(loc);
        if (item == null || item == Items.AIR) {
            LOGGER.error("Предмет не знайдено в реєстрі гри: " + itemId);
            return;
        }

        int remaining = count;
        ItemStack tempStack = new ItemStack(item);
        if (nbt != null) {
            tempStack.setTag(nbt.copy());
        }
        int maxStack = tempStack.getMaxStackSize();

        while (remaining > 0) {
            int stackSize = Math.min(maxStack, remaining);
            ItemStack stack = new ItemStack(item, stackSize);
            
            if (nbt != null) {
                stack.setTag(nbt.copy());
            }

            if (stack.getItem() instanceof ArmorItem armor && nbt == null) {
                EquipmentSlot slot = armor.getEquipmentSlot();
                if (player.getItemBySlot(slot).isEmpty()) {
                    player.setItemSlot(slot, stack);
                    remaining -= stackSize;
                    continue;
                }
            }

            boolean added = player.getInventory().add(stack);
            if (!added) {
                player.drop(stack, false);
            }

            remaining -= stackSize;
        }
    }
}