package com.tacticalpvp.mod;

import com.tacticalpvp.mod.commands.*;
import com.tacticalpvp.mod.item.ModItems;
import com.tacticalpvp.mod.menu.ModMenus;
import com.tacticalpvp.mod.network.NetworkHandler;
import com.tacticalpvp.mod.match.MatchEventHandler;
import com.tacticalpvp.mod.points.CaptureTickHandler;
import com.tacticalpvp.mod.punish.PunishTickHandler;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Головний клас мода "Tactical PvP / Sector Control".
 *
 * Тут відбувається:
 *  - реєстрація предметів, меню (GUI), мережевих пакетів
 *  - реєстрація креативної вкладки мода
 *  - реєстрація адмін-команд (/point, /match, /teamzone, /punish, /kit)
 *  - підписка на тікові обробники (капчер точок, таймер в'язниці, логіка матчу)
 */
@Mod(TacticalPvpMod.MODID)
public class TacticalPvpMod {

    public static final String MODID = "tacticalpvp";

    /** Глобальний менеджер лімітів класів (скидається при /match init). */
    public static final com.tacticalpvp.mod.kits.ClassLimitManager CLASS_LIMIT_MANAGER =
            new com.tacticalpvp.mod.kits.ClassLimitManager();

    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<CreativeModeTab> TACTICAL_TAB = CREATIVE_TABS.register("tactical_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.tacticalpvp.tactical_tab"))
                    .icon(() -> new net.minecraft.world.item.ItemStack(ModItems.ENDER_EYE_KIT.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.ENDER_EYE_KIT.get());
                    })
                    .build());

    public TacticalPvpMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.ITEMS.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(com.tacticalpvp.mod.menu.ClientMenuSetup::onClientSetup);

        // Реєстрація серверних тікових обробників
        MinecraftForge.EVENT_BUS.register(new CaptureTickHandler());
        MinecraftForge.EVENT_BUS.register(new PunishTickHandler());
        MinecraftForge.EVENT_BUS.register(new MatchEventHandler());
        MinecraftForge.EVENT_BUS.register(this);

        // Реєстрація кастомних GUI-оверлеїв (компас + рахунок/таймер) відбувається
        // окремо у ClientGuiOverlays через RegisterGuiOverlaysEvent на mod bus.
        modEventBus.addListener(com.tacticalpvp.mod.hud.ClientGuiOverlays::onRegisterOverlays);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(NetworkHandler::register);
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        PointCommands.register(event.getDispatcher());
        MatchCommands.register(event.getDispatcher());
        TeamZoneCommands.register(event.getDispatcher());
        PunishCommands.register(event.getDispatcher());
        KitCommands.register(event.getDispatcher());
    }
}
