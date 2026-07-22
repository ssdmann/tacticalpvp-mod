package com.tacticalpvp.mod.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Меню вибору класу. Не має ігрових слотів — лише 4 клікабельні кнопки на клієнті
 * (Assault / Grenadier / Drone Pilot / Engineer), що надсилають SelectClassPacket на сервер.
 */
public class ClassSelectionMenu extends AbstractContainerMenu {

    public ClassSelectionMenu(int containerId, Inventory playerInventory) {
        super(ModMenus.CLASS_SELECTION_MENU.get(), containerId);
    }

    @Override
    public net.minecraft.world.item.ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int index) {
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(net.minecraft.world.entity.player.Player player) {
        return true;
    }
}
