package com.tacticalpvp.mod.kits;

public enum PlayerClass {
    ASSAULT("Штурмовик"),
    GRENADIER("Гранатометник"),
    DRONE_PILOT("Пілот дрону"),
    ENGINEER("Інженер");

    public final String uaName;

    PlayerClass(String uaName) {
        this.uaName = uaName;
    }

    public String configKey() {
        return name().toLowerCase();
    }

    public static PlayerClass fromConfigKey(String key) {
        for (PlayerClass c : values()) {
            if (c.configKey().equals(key.toLowerCase())) return c;
        }
        throw new IllegalArgumentException("Невідомий клас: " + key);
    }
}
