package com.tacticalpvp.match;

import net.minecraft.ChatFormatting;

/**
 * The two playable teams. NONE is used to represent a neutral / uncaptured capture point.
 */
public enum TeamColor {
    NONE("neutral", ChatFormatting.GRAY),
    RED("red", ChatFormatting.RED),
    BLUE("blue", ChatFormatting.BLUE);

    private final String key;
    private final ChatFormatting formatting;

    TeamColor(String key, ChatFormatting formatting) {
        this.key = key;
        this.formatting = formatting;
    }

    public String getKey() {
        return key;
    }

    public ChatFormatting getFormatting() {
        return formatting;
    }

    public TeamColor opposite() {
        if (this == RED) return BLUE;
        if (this == BLUE) return RED;
        return NONE;
    }

    public static TeamColor fromString(String s) {
        for (TeamColor t : values()) {
            if (t.key.equalsIgnoreCase(s)) return t;
        }
        return NONE;
    }
}
