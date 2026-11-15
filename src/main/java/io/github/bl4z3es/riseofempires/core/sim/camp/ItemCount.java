package io.github.bl4z3es.riseofempires.core.sim.camp;

import java.util.ArrayList;
import java.util.List;

/** A number of one item, for example 24 {@code minecraft:wheat_seeds}. */
public record ItemCount(String item, int count) {
    public String encode() {
        return item + "=" + count;
    }

    public static ItemCount decode(String s) {
        int eq = s.lastIndexOf('=');
        return new ItemCount(s.substring(0, eq), Integer.parseInt(s.substring(eq + 1)));
    }

    public static List<String> encodeAll(List<ItemCount> items) {
        List<String> out = new ArrayList<>();
        for (ItemCount i : items) out.add(i.encode());
        return out;
    }

    public static List<ItemCount> decodeAll(List<String> items) {
        List<ItemCount> out = new ArrayList<>();
        for (String s : items) out.add(decode(s));
        return out;
    }

    /** "24 wheat seeds, 6 oak log" */
    public static String describe(List<ItemCount> items) {
        StringBuilder sb = new StringBuilder();
        for (ItemCount i : items) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(i.count()).append(' ').append(Food.itemName(i.item()));
        }
        return sb.toString();
    }
}
