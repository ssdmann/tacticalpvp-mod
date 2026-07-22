package com.tacticalpvp.mod.kits;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Один запис у конфігурації кіта. Може бути:
 *  1) простим предметом ("item": "minecraft:iron_sword", "count": 1)
 *  2) рандомізованим пулом зброї ("type": "random_weapon", "options": [...])
 *
 * Формат JSON для рандомізованого слоту (див. README для детальних підказок):
 * {
 *   "type": "random_weapon",
 *   "options": [
 *     { "item": "tacticalpvp:rifle_ak",  "weight": 50, "count": 1, "ammoItem": "tacticalpvp:ammo_762", "ammoCount": 90 },
 *     { "item": "tacticalpvp:rifle_m4",  "weight": 30, "count": 1, "ammoItem": "tacticalpvp:ammo_556", "ammoCount": 90 },
 *     { "item": "tacticalpvp:shotgun",   "weight": 20, "count": 1, "ammoItem": "tacticalpvp:ammo_12ga", "ammoCount": 40 }
 *   ]
 * }
 * weight — відносна вага (не обов'язково має сумарно давати 100, ваги нормалізуються).
 */
public class KitEntry {

    public static class WeightedOption {
        public String item;
        public int count = 1;
        public double weight = 1.0;
        public String ammoItem; // може бути null
        public int ammoCount = 0;
    }

    public boolean isRandom;
    public String simpleItem;
    public int simpleCount = 1;
    public List<WeightedOption> options = new ArrayList<>();

    public static KitEntry parse(JsonObject obj) {
        KitEntry entry = new KitEntry();
        String type = obj.has("type") ? obj.get("type").getAsString() : "item";
        if ("random_weapon".equals(type)) {
            entry.isRandom = true;
            obj.getAsJsonArray("options").forEach(el -> {
                JsonObject o = el.getAsJsonObject();
                WeightedOption wo = new WeightedOption();
                wo.item = o.get("item").getAsString();
                wo.count = o.has("count") ? o.get("count").getAsInt() : 1;
                wo.weight = o.has("weight") ? o.get("weight").getAsDouble() : 1.0;
                if (o.has("ammoItem")) wo.ammoItem = o.get("ammoItem").getAsString();
                wo.ammoCount = o.has("ammoCount") ? o.get("ammoCount").getAsInt() : 0;
                entry.options.add(wo);
            });
        } else {
            entry.isRandom = false;
            entry.simpleItem = obj.get("item").getAsString();
            entry.simpleCount = obj.has("count") ? obj.get("count").getAsInt() : 1;
        }
        return entry;
    }

    /** Обирає зважено-випадковий варіант зброї з ammoItem/ammoCount що йде разом. */
    public WeightedOption rollRandom(java.util.Random random) {
        double total = options.stream().mapToDouble(o -> o.weight).sum();
        double roll = random.nextDouble() * total;
        double acc = 0;
        for (WeightedOption o : options) {
            acc += o.weight;
            if (roll <= acc) return o;
        }
        return options.get(options.size() - 1);
    }
}
