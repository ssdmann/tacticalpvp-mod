package com.tacticalpvp.mod.kits;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.tacticalpvp.mod.util.Team;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.FileReader;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class KitConfigLoader {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<Team, Map<PlayerClass, ClassKitConfig>> CACHE = new EnumMap<>(Team.class);

    public static class ClassKitConfig {
        public double percentLimit = 25.0;
        public List<KitEntry> items = new ArrayList<>();
    }

    public static Path configDir() {
        return FMLPaths.CONFIGDIR.get().resolve("tacticalpvp").resolve("kits");
    }

    public static void loadAll() {
        CACHE.clear();
        ensureDefaultConfigsExist();

        for (Team team : new Team[]{Team.RED, Team.BLUE}) {
            Map<PlayerClass, ClassKitConfig> map = new EnumMap<>(PlayerClass.class);
            for (PlayerClass cls : PlayerClass.values()) {
                Path file = configDir().resolve(team.name().toLowerCase()).resolve(cls.configKey() + ".json");
                ClassKitConfig cfg = new ClassKitConfig();

                if (Files.exists(file) && isNonEmpty(file)) {
                    try (FileReader reader = new FileReader(file.toFile())) {
                        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                        cfg.percentLimit = root.has("percentLimit") ? root.get("percentLimit").getAsDouble() : 25.0;

                        JsonArray items = root.getAsJsonArray("items");
                        if (items != null) {
                            for (var el : items) {
                                cfg.items.add(KitEntry.parse(el.getAsJsonObject()));
                            }
                        }
                        LOGGER.info("Успішно завантажено кіт: " + team.name() + " -> " + cls.configKey() + " (Предметів: " + cfg.items.size() + ")");
                    } catch (Exception e) {
                        LOGGER.error("Помилка читання конфігу кіта: " + file, e);
                    }
                } else {
                    LOGGER.warn("Конфіг файл порожній або відсутній: " + file + ". Використовуються стандартні значення.");
                }
                map.put(cls, cfg);
            }
            CACHE.put(team, map);
        }
    }

    private static boolean isNonEmpty(Path path) {
        try {
            return Files.size(path) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void ensureDefaultConfigsExist() {
        try {
            Path baseDir = configDir();
            if (!Files.exists(baseDir)) {
                Files.createDirectories(baseDir);
            }

            for (Team team : new Team[]{Team.RED, Team.BLUE}) {
                String teamName = team.name().toLowerCase();
                Path teamDir = baseDir.resolve(teamName);
                if (!Files.exists(teamDir)) {
                    Files.createDirectories(teamDir);
                }

                for (PlayerClass cls : PlayerClass.values()) {
                    String fileName = cls.configKey() + ".json";
                    Path targetFile = teamDir.resolve(fileName);

                    // Якщо файлу немає АБО він порожній — копіюємо дефолтний
                    if (!Files.exists(targetFile) || !isNonEmpty(targetFile)) {
                        String resourcePath = "/assets/tacticalpvp/kits/" + teamName + "/" + fileName;
                        try (InputStream is = KitConfigLoader.class.getResourceAsStream(resourcePath)) {
                            if (is != null) {
                                Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
                                LOGGER.info("Створено дефолтний конфіг: " + targetFile.toAbsolutePath());
                            } else {
                                LOGGER.warn("Шаблон у ресурсах не знайдено: " + resourcePath);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Помилка при створенні папок/файлів конфігів", e);
        }
    }

    public static ClassKitConfig get(Team team, PlayerClass cls) {
        if (!CACHE.containsKey(team)) loadAll();
        return CACHE.get(team).get(cls);
    }

    public static double percentLimit(Team team, PlayerClass cls) {
        ClassKitConfig cfg = get(team, cls);
        return cfg != null ? cfg.percentLimit : 25.0;
    }
}