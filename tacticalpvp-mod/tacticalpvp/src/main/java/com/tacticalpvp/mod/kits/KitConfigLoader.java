package com.tacticalpvp.mod.kits;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tacticalpvp.mod.util.Team;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Завантажує конфіги кітів з `config/tacticalpvp/kits/<red|blue>/<class>.json`.
 * Кожна команда має ПОВНІСТЮ ізольовану конфігурацію — жодного витоку лутів між командами.
 *
 * Формат файлу класу (напр. config/tacticalpvp/kits/red/assault.json):
 * {
 *   "percentLimit": 50,
 *   "items": [
 *     { "item": "minecraft:iron_chestplate", "count": 1 },
 *     { "type": "random_weapon", "options": [ ... ] }
 *   ]
 * }
 */
public class KitConfigLoader {

    private static final Map<Team, Map<PlayerClass, ClassKitConfig>> CACHE = new EnumMap<>(Team.class);

    public static class ClassKitConfig {
        public double percentLimit;
        public List<KitEntry> items = new ArrayList<>();
    }

    public static Path configDir() {
        return Path.of("config", "tacticalpvp", "kits");
    }

    /** Викликати при старті сервера / after /kit reload, аби підхопити зміни конфігу без ребілду. */
    public static void loadAll() {
        CACHE.clear();
        for (Team team : new Team[]{Team.RED, Team.BLUE}) {
            Map<PlayerClass, ClassKitConfig> map = new EnumMap<>(PlayerClass.class);
            for (PlayerClass cls : PlayerClass.values()) {
                Path file = configDir().resolve(team.name().toLowerCase()).resolve(cls.configKey() + ".json");
                ClassKitConfig cfg = new ClassKitConfig();
                if (Files.exists(file)) {
                    try (FileReader reader = new FileReader(file.toFile())) {
                        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                        cfg.percentLimit = root.has("percentLimit") ? root.get("percentLimit").getAsDouble() : 25.0;
                        JsonArray items = root.getAsJsonArray("items");
                        if (items != null) {
                            for (var el : items) {
                                cfg.items.add(KitEntry.parse(el.getAsJsonObject()));
                            }
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("Не вдалося завантажити кіт-конфіг: " + file, e);
                    }
                } else {
                    // Дефолтні відсотки, якщо файл ще не створений адміном
                    cfg.percentLimit = switch (cls) {
                        case ASSAULT -> 50.0;
                        case GRENADIER -> 20.0;
                        case DRONE_PILOT -> 10.0;
                        case ENGINEER -> 20.0;
                    };
                }
                map.put(cls, cfg);
            }
            CACHE.put(team, map);
        }
    }

    public static ClassKitConfig get(Team team, PlayerClass cls) {
        if (!CACHE.containsKey(team)) loadAll();
        return CACHE.get(team).get(cls);
    }

    public static double percentLimit(Team team, PlayerClass cls) {
        return get(team, cls).percentLimit;
    }
}
