package io.github.bl4z3es.riseofempires.core.sim;

import io.github.bl4z3es.riseofempires.core.model.Stockpile;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** How much a person eats from each item, in quarter-meals. Items not listed are not food. */
public final class FoodValues {
	private final Map<String, Integer> quartersPerItem;

	public FoodValues(Map<String, Integer> quartersPerItem) {
		this.quartersPerItem = Collections.unmodifiableMap(new TreeMap<>(quartersPerItem));
	}

	/** Starting values. All of them are untested guesses. */
	public static FoodValues defaults() {
		Map<String, Integer> m = new TreeMap<>();
		// PROPOSED
		m.put("minecraft:wheat", 4);
		m.put("minecraft:bread", 12);
		m.put("minecraft:carrot", 4);
		m.put("minecraft:potato", 4);
		m.put("minecraft:baked_potato", 6);
		m.put("minecraft:beetroot", 2);
		m.put("minecraft:apple", 4);
		m.put("minecraft:cooked_beef", 12);
		m.put("minecraft:cooked_porkchop", 12);
		m.put("minecraft:cooked_chicken", 8);
		m.put("minecraft:cooked_mutton", 8);
		m.put("minecraft:cooked_cod", 6);
		m.put("minecraft:cooked_salmon", 8);
		m.put("minecraft:sweet_berries", 2);
		m.put("minecraft:melon_slice", 2);
		m.put("minecraft:pumpkin_pie", 12);
		m.put("minecraft:mushroom_stew", 8);
		m.put("minecraft:beetroot_soup", 8);
		return new FoodValues(m);
	}

	/** Quarter-meals one item feeds, or 0 if it is not food. */
	public int quarters(String item) { return quartersPerItem.getOrDefault(item, 0); }

	public boolean isFood(String item) { return quartersPerItem.containsKey(item); }

	/** Every food item and its value, sorted by item id. The map cannot be changed. */
	public Map<String, Integer> all() { return quartersPerItem; }

	/** Quarter-meals of food in a store. */
	public int totalQuarters(Stockpile s) {
		int sum = 0;
		for (Map.Entry<String, Integer> e : quartersPerItem.entrySet()) sum += s.total(e.getKey()) * e.getValue();
		return sum;
	}
}
