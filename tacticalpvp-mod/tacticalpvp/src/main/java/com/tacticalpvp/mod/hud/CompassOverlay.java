package com.tacticalpvp.mod.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.overlay.IGuiOverlay;
import net.minecraft.util.Mth;

/**
 * Азимутальний компас — горизонтальна стрічка у верхній частині екрана.
 * Показує N/E/S/W та градуси, плавно рухається залежно від yaw камери гравця.
 *
 * Реалізовано без мапи текстур: стрічка малюється лінійно, поточний кут
 * гравця завжди у центрі, а мітки сторін світу зсуваються відносно нього.
 */
public class CompassOverlay implements IGuiOverlay {

    private static final int RIBBON_HEIGHT = 14;
    private static final int PIXELS_PER_DEGREE = 4; // "щільність" стрічки
    private static float smoothedYaw = 0f;

    @Override
    public void render(net.minecraftforge.client.gui.overlay.ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        float rawYaw = Mth.wrapDegrees(mc.player.getViewYRot(partialTick));
        // Плавне згладжування (lerp) — щоб рух стрічки не був різким
        smoothedYaw = Mth.approachDegrees(smoothedYaw, rawYaw, 6.0f);

        int centerX = screenWidth / 2;
        int top = 2;

        graphics.fill(0, top, screenWidth, top + RIBBON_HEIGHT, 0x66000000);

        // Малюємо мітки кожні 15 градусів у діапазоні +-100 градусів навколо поточного погляду
        for (int deg = -180; deg <= 180; deg += 15) {
            float relative = Mth.wrapDegrees(deg - smoothedYaw);
            int x = centerX + Math.round(relative * PIXELS_PER_DEGREE / 15f) * 15 / 15; // спрощена лінійна проєкція
            int screenX = centerX + (int) (relative * PIXELS_PER_DEGREE / 15.0);
            if (screenX < 0 || screenX > screenWidth) continue;

            String label = directionLabel(deg);
            int color = label.isEmpty() ? 0xFFAAAAAA : 0xFFFFFFFF;
            graphics.drawCenteredString(mc.font, label.isEmpty() ? Integer.toString(Math.floorMod(deg, 360)) : label,
                    screenX, top + 2, color);
        }

        // Вертикальний маркер по центру = точний напрямок погляду гравця
        graphics.fill(centerX - 1, top, centerX + 1, top + RIBBON_HEIGHT, 0xFFFFFFFF);
    }

    private String directionLabel(int deg) {
        int normalized = Math.floorMod(deg, 360);
        return switch (normalized) {
            case 0 -> "S";   // у Minecraft yaw=0 дивиться на південь
            case 90 -> "W";
            case 180 -> "N";
            case 270 -> "E";
            default -> "";
        };
    }
}
