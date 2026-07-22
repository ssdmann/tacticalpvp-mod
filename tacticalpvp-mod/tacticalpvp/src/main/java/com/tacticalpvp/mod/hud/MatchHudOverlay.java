package com.tacticalpvp.mod.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraft.network.chat.Component;

/**
 * Показує:
 *  - рахунок Червоні/Сині + таймер матчу, що лишився (зсунуто нижче компаса, п.6)
 *  - ненав'язливий індикатор прогресу захоплення точки у зоні Action Bar
 *    ("Захоплення точки 2: 45%"), видимий лише гравцям всередині зони.
 */
public class MatchHudOverlay implements IGuiOverlay {

    // Оригінально кіл/смерть-лічильник і таймер малювались від Y=2.
    // Зсуваємо на висоту стрічки компаса + невеликий відступ, щоб не перекривались (п.6).
    private static final int SHIFTED_TOP = 18;

    @Override
    public void render(net.minecraftforge.client.gui.overlay.ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        String scoreLine = "§c" + ClientHudState.redScore + "§r : §9" + ClientHudState.blueScore;
        String timeLine = formatTime(ClientHudState.remainingSeconds);

        int centerX = screenWidth / 2;
        graphics.drawCenteredString(mc.font, scoreLine, centerX, SHIFTED_TOP, 0xFFFFFF);
        graphics.drawCenteredString(mc.font, timeLine, centerX, SHIFTED_TOP + 10, 0xFFFFFF);

        if (ClientHudState.captureActive) {
            Component msg = Component.translatable("hud.tacticalpvp.capturing",
                    ClientHudState.capturePointIndex, ClientHudState.capturePercent);
            graphics.drawCenteredString(mc.font, msg, centerX, screenHeight - 48, 0xFFFFFF);
        }
    }

    private String formatTime(int totalSeconds) {
        int m = totalSeconds / 60;
        int s = totalSeconds % 60;
        return String.format("%02d:%02d", m, s);
    }
}
