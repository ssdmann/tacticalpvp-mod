package com.tacticalpvp.mod.item;

import com.tacticalpvp.mod.TacticalPvpMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, TacticalPvpMod.MODID);

    // Кастомне око видалено! Тепер використовується ванільне net.minecraft.world.item.Items.ENDER_EYE
}