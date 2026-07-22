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
 * Динамічний клієнтський GUI вибору класу.
 * Автоматично підлаштовує розміри та розташування кнопок під кількість класів.
 */
public class ClassSelectionScreen extends AbstractContainerScreen<ClassSelectionMenu> {

    private static final int BTN_WIDTH = 140;
    private static final int BTN_HEIGHT = 20;
    private static final int SPACING = 6;

    public ClassSelectionScreen(ClassSelectionMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void init() {
        PlayerClass[] classes = PlayerClass.values();
        int total = classes.length;

        // Динамічно рахуємо кількість стовпчиків та рядків
        int cols = total > 4 ? 2 : 1;
        int rows = (int) Math.ceil((double) total / cols);

        // Динамічно вираховуємо ширину та висоту вікна
        this.imageWidth = cols * BTN_WIDTH + (cols + 1) * 15;
        this.imageHeight = rows * BTN_HEIGHT + (rows + 1) * SPACING + 25;

        super.init();
        this.clearWidgets();

        int startX = this.leftPos + 15;
        int startY = this.topPos + 25;

        for (int i = 0; i < total; i++) {
            PlayerClass cls = classes[i];
            int col = i % cols;
            int row = i / cols;

            int x = startX + col * (BTN_WIDTH + 10);
            int y = startY + row * (BTN_HEIGHT + SPACING);

            // Створюємо кнопку з українською назвою класу
            this.addRenderableWidget(Button.builder(Component.literal(cls.uaName), btn -> {
                        NetworkHandler.CHANNEL.sendToServer(new SelectClassPacket(cls));
                        this.onClose();
                    })
                    .bounds(x, y, BTN_WIDTH, BTN_HEIGHT)
                    .build());
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // Напівпрозорий темний рамковий фон, що динамічно адаптується під imageWidth / imageHeight
        graphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xDD101010);
        graphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + 2, 0xFF444444);
        graphics.fill(this.leftPos, this.topPos + this.imageHeight - 2, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xFF444444);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        // Малюємо заголовок по центру меню
        graphics.drawCenteredString(this.font, this.title, this.leftPos + (this.imageWidth / 2), this.topPos + 8, 0xFFFFFF);
    }
}