package io.github.bl4z3es.riseofempires.core.sim.camp;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.model.Stockpile;
import java.util.LinkedHashMap;
import java.util.Map;

/** Which items count as food and how much of a meal each one is. */
public final class Food {
    private Food() {}

    public static final String BREAD = "minecraft:bread";
    public static final String WHEAT = "minecraft:wheat";
    public static final String SEEDS = "minecraft:wheat_seeds";
    public static final String OAK_LOG = "minecraft:oak_log";
    public static final String COBBLESTONE = "minecraft:cobblestone";

    /** Meal value per item, best first. People eat the best food in the granary first. */
    public static final Map<String, Double> MEAL_VALUES;

    static {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put(BREAD, Balance.MEAL_VALUE_BREAD);
        m.put("minecraft:cooked_beef", Balance.MEAL_VALUE_COOKED);
        m.put("minecraft:cooked_porkchop", Balance.MEAL_VALUE_COOKED);
        m.put("minecraft:cooked_mutton", Balance.MEAL_VALUE_COOKED);
        m.put("minecraft:cooked_chicken", Balance.MEAL_VALUE_COOKED);
        m.put("minecraft:cooked_cod", Balance.MEAL_VALUE_COOKED);
        m.put("minecraft:cooked_salmon", Balance.MEAL_VALUE_COOKED);
        m.put("minecraft:baked_potato", Balance.MEAL_VALUE_COOKED);
        m.put("minecraft:apple", Balance.MEAL_VALUE_PRODUCE);
        m.put("minecraft:carrot", Balance.MEAL_VALUE_PRODUCE);
        m.put("minecraft:potato", Balance.MEAL_VALUE_PRODUCE);
        m.put("minecraft:beetroot", Balance.MEAL_VALUE_PRODUCE);
        m.put(WHEAT, Balance.MEAL_VALUE_WHEAT);
        MEAL_VALUES = java.util.Collections.unmodifiableMap(m);
    }

    public static boolean isFood(String item) {
        return MEAL_VALUES.containsKey(item);
    }

    /** Total meals the stockpile could serve. */
    public static double meals(Stockpile stock) {
        double total = 0;
        for (Map.Entry<String, Double> e : MEAL_VALUES.entrySet()) total += stock.count(e.getKey()) * e.getValue();
        return total;
    }

    /** "minecraft:wheat_seeds" becomes "wheat seeds". */
    public static String itemName(String item) {
        int colon = item.indexOf(':');
        return item.substring(colon + 1).replace('_', ' ');
    }
}
