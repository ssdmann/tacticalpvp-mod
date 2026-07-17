package com.tacticalpvp.client;

import com.tacticalpvp.match.MatchState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;

/**
 * Minimal, non-intrusive top-center HUD: "RED  12 : 7  BLUE" with the remaining
 * match time centered just beneath it. Hidden entirely while no match is running.
 */
@Mod.EventBusSubscriber(modid = "tacticalpvp", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class HudOverlay {

    public static final IGuiOverlay HUD = HudOverlay::render;

    private static void render(net.minecraftforge.client.gui.overlay.ForgeGui gui, GuiGraphics graphics,
                                float partialTick, int screenWidth, int screenHeight) {
        if (ClientHudState.getState() == MatchState.IDLE) {
            return; // nothing to show outside an active/ended round
        }

        Minecraft mc = Minecraft.getInstance();
        var font = mc.font;

        int red = ClientHudState.getRedScore();
        int blue = ClientHudState.getBlueScore();

        String redText = "RED " + red;
        String sep = " : ";
        String blueText = blue + " BLUE";
        String scoreLine = redText + sep + blueText;

        int scoreWidth = font.width(scoreLine);
        int x = (screenWidth - scoreWidth) / 2;
        int y = 6;

        // Draw team-colored halves plus a neutral separator for readability.
        int cursorX = x;
        cursorX = graphics.drawString(font, redText, cursorX, y, 0xFF5555, true);
        cursorX = graphics.drawString(font, sep, cursorX, y, 0xFFFFFF, true);
        graphics.drawString(font, blueText, cursorX, y, 0x5599FF, true);

        String timeText = ClientHudState.formatTime();
        boolean paused = ClientHudState.getState() == MatchState.PAUSED;
        if (paused) {
            timeText = timeText + " (PAUSED)";
        }
        int timeWidth = font.width(timeText);
        int timeColor = paused ? 0xFFDD55 : 0xFFFFFF;
        graphics.drawString(font, timeText, (screenWidth - timeWidth) / 2, y + 10, timeColor, true);
    }
}
