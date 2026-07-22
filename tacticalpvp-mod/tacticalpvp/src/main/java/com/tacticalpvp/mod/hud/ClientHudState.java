package com.tacticalpvp.mod.hud;

public class ClientHudState {
    public static volatile boolean captureActive = false;
    public static volatile int capturePointIndex = -1;
    public static volatile int capturePercent = 0;

    public static volatile int redScore = 0;
    public static volatile int blueScore = 0;
    public static volatile int remainingSeconds = 0;

    public static void setCaptureProgress(boolean active, int index, int percent) {
        captureActive = active;
        capturePointIndex = index;
        capturePercent = percent;
    }

    public static void setMatchInfo(int red, int blue, int seconds) {
        redScore = red;
        blueScore = blue;
        remainingSeconds = seconds;
    }
}
