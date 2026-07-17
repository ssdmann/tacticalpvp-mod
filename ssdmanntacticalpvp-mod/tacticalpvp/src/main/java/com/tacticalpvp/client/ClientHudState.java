package com.tacticalpvp.client;

import com.tacticalpvp.match.MatchState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Simple client-side cache of the latest HUD sync data, read by the render overlay. */
@OnlyIn(Dist.CLIENT)
public final class ClientHudState {

    private static int redScore = 0;
    private static int blueScore = 0;
    private static int remainingTicks = 0;
    private static boolean infiniteTime = false;
    private static MatchState state = MatchState.IDLE;

    private ClientHudState() {}

    public static void update(int red, int blue, int ticks, boolean infinite, MatchState newState) {
        redScore = red;
        blueScore = blue;
        remainingTicks = ticks;
        infiniteTime = infinite;
        state = newState;
    }

    public static int getRedScore() {
        return redScore;
    }

    public static int getBlueScore() {
        return blueScore;
    }

    public static int getRemainingTicks() {
        return remainingTicks;
    }

    public static boolean isInfiniteTime() {
        return infiniteTime;
    }

    public static MatchState getState() {
        return state;
    }

    public static String formatTime() {
        if (infiniteTime) return "\u221E"; // infinity symbol
        int totalSeconds = Math.max(0, remainingTicks / 20);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
