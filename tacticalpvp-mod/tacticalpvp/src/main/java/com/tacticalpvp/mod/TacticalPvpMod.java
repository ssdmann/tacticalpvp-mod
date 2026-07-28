package com.tacticalpvp.mod;

import com.tacticalpvp.mod.commands.*;
import com.tacticalpvp.mod.item.ModItems;
import com.tacticalpvp.mod.kits.KitConfigLoader;
import com.tacticalpvp.mod.menu.ModMenus;
import com.tacticalpvp.mod.network.NetworkHandler;
import com.tacticalpvp.mod.match.MatchEventHandler;
import com.tacticalpvp.mod.points.CaptureTickHandler;
import com.tacticalpvp.mod.punish.PunishTickHandler;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Головний клас мода "Tactical PvP / Sector Control".
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
                    .icon(() -> new ItemStack(Items.ENDER_EYE))
                    .displayItems((params, output) -> {
                        output.accept(Items.ENDER_EYE);
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

        // Реєстрація кастомних GUI-оверлеїв
        modEventBus.addListener(com.tacticalpvp.mod.hud.ClientGuiOverlays::onRegisterOverlays);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            NetworkHandler.register();
            // Завантажуємо та копіюємо конфіги кітів під час ініціалізації
            KitConfigLoader.loadAll();
        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Гарантуємо, що конфіги завантажені в пам'ять при старті світу/сервера
        KitConfigLoader.loadAll();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        PointCommands.register(event.getDispatcher());
        MatchCommands.register(event.getDispatcher());
        TeamZoneCommands.register(event.getDispatcher());
        PunishCommands.register(event.getDispatcher());
        KitCommands.register(event.getDispatcher());
    }
}