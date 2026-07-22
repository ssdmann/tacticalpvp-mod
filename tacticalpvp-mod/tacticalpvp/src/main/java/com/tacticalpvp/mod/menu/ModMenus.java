package com.tacticalpvp.mod.menu;

import com.tacticalpvp.mod.TacticalPvpMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, TacticalPvpMod.MODID);

    public static final RegistryObject<MenuType<ClassSelectionMenu>> CLASS_SELECTION_MENU =
            MENUS.register("class_selection", () -> IForgeMenuType.create((windowId, inv, data) -> new ClassSelectionMenu(windowId, inv, data)));
}