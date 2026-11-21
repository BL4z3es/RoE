package io.github.bl4z3es.riseofempires.core.model;

import java.util.Map;
import java.util.TreeMap;

/**
 * What a settlement's store holds (rule E12). {@code counted} is the last count of the real containers.
 * {@code pending} holds changes made off-camera that are written into the containers when the area loads.
 * Item ids are Minecraft ids as text, such as {@code minecraft:wheat}.
 */
public record Stockpile(long settlementId, Map<String, Integer> counted, Map<String, Integer> pending) {
	public Stockpile {
		counted = Map.copyOf(counted);
		pending = Map.copyOf(pending);
	}

	public static Stockpile empty(long settlementId) { return new Stockpile(settlementId, Map.of(), Map.of()); }

	/** Counted plus pending, never below zero. */
	public int total(String item) {
		return Math.max(0, counted.getOrDefault(item, 0) + pending.getOrDefault(item, 0));
	}

	/** Every item with a positive total, sorted by id. */
	public Map<String, Integer> totals() {
		Map<String, Integer> out = new TreeMap<>();
		for (String k : counted.keySet()) if (total(k) > 0) out.put(k, total(k));
		for (String k : pending.keySet()) if (total(k) > 0) out.put(k, total(k));
		return out;
	}

	public Stockpile withCounted(Map<String, Integer> c) { return new Stockpile(settlementId, c, pending); }

	public Stockpile withPendingChange(String item, int delta) {
		Map<String, Integer> next = new TreeMap<>(pending);
		int v = next.getOrDefault(item, 0) + delta;
		if (v == 0) next.remove(item); else next.put(item, v);
		return new Stockpile(settlementId, counted, next);
	}

	/** After pending changes were written into the containers and they were counted again. */
	public Stockpile reconciled(Map<String, Integer> newCount) { return new Stockpile(settlementId, newCount, Map.of()); }
}
