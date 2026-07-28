package com.tacticalpvp.mod.kits;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class KitEntry {

    public static class WeightedOption {
        public String item;
        public int count = 1;
        public double weight = 1.0;
        public String ammoItem;
        public int ammoCount = 0;
        public CompoundTag nbt;
        public CompoundTag ammoNbt;
    }

    public boolean isRandom;
    public String simpleItem;
    public int simpleCount = 1;
    public CompoundTag simpleNbt;
    public List<WeightedOption> options = new ArrayList<>();

    public static KitEntry parse(JsonObject obj) {
        KitEntry entry = new KitEntry();
        String type = obj.has("type") ? obj.get("type").getAsString() : "item";

        if ("random_weapon".equals(type)) {
            entry.isRandom = true;
            if (obj.has("options") && obj.get("options").isJsonArray()) {
                JsonArray optionsArray = obj.getAsJsonArray("options");
                optionsArray.forEach(el -> {
                    if (!el.isJsonObject()) return;
                    JsonObject o = el.getAsJsonObject();
                    
                    WeightedOption wo = new WeightedOption();
                    wo.item = o.has("item") ? o.get("item").getAsString() : "minecraft:air";
                    wo.count = o.has("count") ? o.get("count").getAsInt() : 1;
                    wo.weight = o.has("weight") ? Math.max(0.001, o.get("weight").getAsDouble()) : 1.0;
                    
                    if (o.has("ammoItem")) wo.ammoItem = o.get("ammoItem").getAsString();
                    wo.ammoCount = o.has("ammoCount") ? o.get("ammoCount").getAsInt() : 0;

                    // Зчитування NBT для зброї
                    if (o.has("nbt")) {
                        try {
                            wo.nbt = TagParser.parseTag(o.get("nbt").getAsString());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    // Зчитування NBT для патронів
                    if (o.has("ammoNbt")) {
                        try {
                            wo.ammoNbt = TagParser.parseTag(o.get("ammoNbt").getAsString());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    
                    entry.options.add(wo);
                });
            }
        } else {
            entry.isRandom = false;
            entry.simpleItem = obj.has("item") ? obj.get("item").getAsString() : "minecraft:air";
            entry.simpleCount = obj.has("count") ? obj.get("count").getAsInt() : 1;

            // Зчитування NBT для звичайних предметів (наприклад, незнищенна лопата)
            if (obj.has("nbt")) {
                try {
                    entry.simpleNbt = TagParser.parseTag(obj.get("nbt").getAsString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return entry;
    }

    public WeightedOption rollRandom(Random random) {
        if (options.isEmpty()) {
            return null;
        }

        double total = options.stream().mapToDouble(o -> o.weight).sum();
        if (total <= 0) {
            return options.get(0);
        }

        double roll = random.nextDouble() * total;
        double acc = 0;

        for (WeightedOption o : options) {
            acc += o.weight;
            if (roll <= acc) {
                return o;
            }
        }

        return options.get(options.size() - 1);
    }
}