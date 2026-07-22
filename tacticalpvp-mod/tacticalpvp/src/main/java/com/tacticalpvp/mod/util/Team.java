package com.tacticalpvp.mod.util;

/**
 * Команда гравця / власник точки захоплення.
 * NEUTRAL використовується лише для точок захоплення (не для гравців).
 */
public enum Team {
    NEUTRAL,
    RED,
    BLUE;

    public static Team fromString(String s) {
        return switch (s.toLowerCase()) {
            case "red", "червоні", "червона" -> RED;
            case "blue", "сині", "синя" -> BLUE;
            default -> NEUTRAL;
        };
    }

    public Team opposite() {
        if (this == RED) return BLUE;
        if (this == BLUE) return RED;
        return NEUTRAL;
    }

    /** Заголовок для повного екрана при виборі команди / оголошенні перемоги. */
    public String fullTitleWord() {
        return this == BLUE ? "СИНІ" : (this == RED ? "ЧЕРВОНІ" : "");
    }
}
