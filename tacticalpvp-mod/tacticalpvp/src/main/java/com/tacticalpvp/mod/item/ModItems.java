package com.tacticalpvp.mod.item;

import com.tacticalpvp.mod.TacticalPvpMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, TacticalPvpMod.MODID);

    public static final RegistryObject<Item> ENDER_EYE_KIT = ITEMS.register("ender_eye_kit",
            () -> new EnderEyeKitItem(new Item.Properties().stacksTo(1)));
}
