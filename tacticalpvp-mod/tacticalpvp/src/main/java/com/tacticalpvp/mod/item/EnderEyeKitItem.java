package com.tacticalpvp.mod.item;

import com.tacticalpvp.mod.menu.ClassSelectionMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * Предмет "Око Ендера" для вибору класу.
 * ПКМ відкриває СИЛЕНТНЕ (без звуку) GUI з 4 класами.
 * Звичайну телепортаційну поведінку ванільного Ока Ендера НЕ успадковуємо —
 * це повністю окремий предмет, що імітує вигляд, але має власну логіку.
 */
public class EnderEyeKitItem extends Item {

    public EnderEyeKitItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            // Без звукового ефекту — тихе відкриття, як вимагає специфікація.
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider(
                            (id, inv, p) -> new ClassSelectionMenu(id, inv),
                            Component.translatable("gui.tacticalpvp.class_selection.title")));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
