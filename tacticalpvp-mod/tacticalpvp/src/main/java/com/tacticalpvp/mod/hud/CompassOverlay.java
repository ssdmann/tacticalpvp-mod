package com.tacticalpvp.mod.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Мінімалістичний широкий азимутальний компас з українськими позначеннями сторін світу.
 * Шкала плавно пересувається вліво/вправо відповідно до погляду гравця.
 */
public class CompassOverlay implements IGuiOverlay {

    // Налаштування габаритів та розташування
    private static final int MARGIN_X = 30;          // Відступ від бокових країв екрана (широка стрічка)
    private static final int TOP_Y = 3;              // Відступ від верху екрана
    private static final int HEIGHT = 11;            // Тоненька висота в пікселях
    private static final float VISIBLE_DEGREES = 120.0f; // Кут огляду компаса (скільки градусів видно одночасно)

    private static float smoothedYaw = 0f;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        // Зчитуємо точний кут огляду камери гравця (-180..180)
        float rawYaw = Mth.wrapDegrees(mc.player.getViewYRot(partialTick));
        // Швидке та плавне згладжування руху шкали
        smoothedYaw = Mth.approachDegrees(smoothedYaw, rawYaw, 12.0f);

        int leftX = MARGIN_X;
        int rightX = screenWidth - MARGIN_X;
        int width = rightX - leftX;
        int centerX = screenWidth / 2;

        // 1. Напівпрозоре темне тло
        graphics.fill(leftX, TOP_Y, rightX, TOP_Y + HEIGHT, 0x33000000);

        // 2. Тонка рамка зверху та знизу
        graphics.fill(leftX, TOP_Y, rightX, TOP_Y + 1, 0x22FFFFFF);
        graphics.fill(leftX, TOP_Y + HEIGHT - 1, rightX, TOP_Y + HEIGHT, 0x22FFFFFF);

        float pixelsPerDegree = (float) width / VISIBLE_DEGREES;

        // 3. Крок 30 градусів = РІВНО 2 поділки між сторонами світу (наприклад: Пд -> 30 -> 60 -> Зд)
        for (int deg = -360; deg <= 360; deg += 30) {
            // Рахуємо зсув поділки відносно центру огляду (рух вліво/вправо)
            float relative = Mth.wrapDegrees(deg - smoothedYaw);

            // Малюємо тільки ті поділки, які потрапляють у видиму зону компаса
            if (Math.abs(relative) <= VISIBLE_DEGREES / 2.0f) {
                int x = centerX + Math.round(relative * pixelsPerDegree);

                if (x >= leftX + 4 && x <= rightX - 4) {
                    int normalizedDeg = Math.floorMod(deg, 360);
                    String label = directionLabel(normalizedDeg);

                    if (!label.isEmpty()) {
                        // Сторони світу (Пн, Пд, Зд, Сд) — акцентний золотистий колір
                        graphics.drawCenteredString(mc.font, label, x, TOP_Y + 1, 0xFFFFCC00);
                    } else {
                        // Цілі градуси (30, 60, 120, 150 і т.д.) — акуратний білий/сірий колір
                        graphics.drawCenteredString(mc.font, String.valueOf(normalizedDeg), x, TOP_Y + 1, 0xDDCCCCCC);
                    }
                }
            }
        }

        // 4. Нерухомий червоний покажчик центру (всі інші числа та сторони світу рухаються відносно нього)
        graphics.fill(centerX - 1, TOP_Y, centerX + 1, TOP_Y + HEIGHT, 0xFFFF3333);
    }

    /**
     * Повертає українські назви для основних сторін світу.
     * У Minecraft: yaw = 0° (Південь), 90° (Захід), 180° (Північ), 270° (Схід).
     */
    private String directionLabel(int deg) {
        return switch (deg) {
            case 0 -> "Пд";   // Південь (0°)
            case 90 -> "Зд";  // Захід (90°)
            case 180 -> "Пн"; // Північ (180°)
            case 270 -> "Сд"; // Схід (270°)
            default -> "";
        };
    }
}