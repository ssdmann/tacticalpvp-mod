package com.tacticalpvp.mod.menu;

import com.tacticalpvp.mod.kits.PlayerClass;
import com.tacticalpvp.mod.network.NetworkHandler;
import com.tacticalpvp.mod.network.SelectClassPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Клієнтський GUI вибору класу. Тихе відкриття (без звуку) — просто екран без
 * жодного викликаного звукового ефекту при відкритті.
 */
public class ClassSelectionScreen extends AbstractContainerScreen<ClassSelectionMenu> {

    public ClassSelectionScreen(ClassSelectionMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 200;
        this.imageHeight = 140;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        int startX = this.leftPos + 20;
        int startY = this.topPos + 20;
        PlayerClass[] classes = PlayerClass.values();
        for (int i = 0; i < classes.length; i++) {
            PlayerClass cls = classes[i];
            this.addRenderableWidget(Button.builder(Component.literal(cls.uaName), btn -> {
                        NetworkHandler.CHANNEL.sendToServer(new SelectClassPacket(cls));
                        this.onClose();
                    })
                    .bounds(startX, startY + i * 25, 160, 20)
                    .build());
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // Простий напівпрозорий фон замість текстури — не потребує ресурс-паку
        graphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xCC101010);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
