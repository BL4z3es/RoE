package io.github.bl4z3es.riseofempires.core.model;

import io.github.bl4z3es.riseofempires.core.Wire;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Counts of goods by Minecraft item id (for example "minecraft:wheat"). In a loaded settlement
 * the real containers are the truth and the mc layer keeps this record equal to them (rule E12).
 */
public record Stockpile(String id, Map<String, Integer> items) {
    public Stockpile {
        items = java.util.Collections.unmodifiableMap(new TreeMap<>(items));
    }

    public static Stockpile empty(String id) {
        return new Stockpile(id, Map.of());
    }

    public int count(String item) {
        return items.getOrDefault(item, 0);
    }

    /** Adds (or with a negative amount removes) goods. Never goes below zero. */
    public Stockpile plus(String item, int amount) {
        Map<String, Integer> m = new TreeMap<>(items);
        int now = Math.max(0, count(item) + amount);
        if (now == 0) m.remove(item); else m.put(item, now);
        return new Stockpile(id, m);
    }

    public String encode() {
        List<String> parts = new ArrayList<>();
        items.forEach((k, v) -> parts.add(k + "=" + v));
        return Wire.line(id, Wire.list(parts));
    }

    public static Stockpile decode(String s) {
        String[] f = Wire.fields(s);
        Map<String, Integer> m = new TreeMap<>();
        for (String part : Wire.unlist(f[1])) {
            int eq = part.lastIndexOf('=');
            m.put(part.substring(0, eq), Integer.parseInt(part.substring(eq + 1)));
        }
        return new Stockpile(f[0], m);
    }
}
