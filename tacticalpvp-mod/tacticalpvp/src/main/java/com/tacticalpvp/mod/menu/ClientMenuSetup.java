package com.tacticalpvp.mod.menu;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class ClientMenuSetup {
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ModMenus.CLASS_SELECTION_MENU.get(), ClassSelectionScreen::new));
    }
}
