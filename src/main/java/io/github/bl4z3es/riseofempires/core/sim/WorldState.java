package io.github.bl4z3es.riseofempires.core.sim;

import io.github.bl4z3es.riseofempires.core.Wire;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Nation;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.model.Stockpile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Everything the simulation knows. Reading is open to all. Writing is done only by
 * {@link EffectApplier} while an event is applied (rule E2); the {@code put...} and
 * {@code setStat} methods exist for it, for loading a save and for building test fixtures.
 * Rule code must not call them.
 *
 * <p>Besides the records there is a stat store: any number of named values on any ref, for
 * example {@code stat(Ref.character(id), "hunger")}. A stat that was never set reads as 0.
 */
public final class WorldState {
    private final long seed;
    private long hour;
    private final Map<String, Character> characters = new TreeMap<>();
    private final Map<String, Settlement> settlements = new TreeMap<>();
    private final Map<String, Nation> nations = new TreeMap<>();
    private final Map<String, Stockpile> stockpiles = new TreeMap<>();
    private final Map<String, Map<String, Double>> stats = new TreeMap<>();
    private final Map<String, Map<String, String>> tags = new TreeMap<>();
    /** Settlements a player is watching. Not saved; set by the mc layer. See {@link #embodied}. */
    private final Set<String> embodied = new HashSet<>();

    public WorldState(long seed) {
        this.seed = seed;
    }

    public long seed() { return seed; }

    /** In-game hours since the world began. */
    public long hour() { return hour; }

    public void setHour(long hour) { this.hour = hour; }

    public Character character(String id) { return characters.get(id); }
    public Settlement settlement(String id) { return settlements.get(id); }
    public Nation nation(String id) { return nations.get(id); }
    public Collection<Character> characters() { return characters.values(); }
    public Collection<Settlement> settlements() { return settlements.values(); }
    public Collection<Nation> nations() { return nations.values(); }
    public Collection<Stockpile> stockpiles() { return stockpiles.values(); }

    /** Never null: an unknown id reads as an empty stockpile. */
    public Stockpile stockpile(String id) {
        Stockpile s = stockpiles.get(id);
        return s == null ? Stockpile.empty(id) : s;
    }

    /** Living residents of a settlement, in the settlement's own order. */
    public List<Character> residents(String settlementId) {
        List<Character> out = new ArrayList<>();
        Settlement s = settlements.get(settlementId);
        if (s == null) return out;
        for (String id : s.residents()) {
            Character c = characters.get(id);
            if (c != null && c.alive()) out.add(c);
        }
        return out;
    }

    public double stat(Ref ref, String key) {
        Map<String, Double> m = stats.get(ref.toString());
        return m == null ? 0 : m.getOrDefault(key, 0.0);
    }

    /** All stats set on a ref. Unmodifiable. */
    public Map<String, Double> stats(Ref ref) {
        Map<String, Double> m = stats.get(ref.toString());
        return m == null ? Map.of() : java.util.Collections.unmodifiableMap(m);
    }

    /** A named piece of text on a ref, for example a nation's current decree. "" if unset. */
    public String tag(Ref ref, String key) {
        Map<String, String> m = tags.get(ref.toString());
        return m == null ? "" : m.getOrDefault(key, "");
    }

    public Map<String, String> tags(Ref ref) {
        Map<String, String> m = tags.get(ref.toString());
        return m == null ? Map.of() : java.util.Collections.unmodifiableMap(m);
    }

    /**
     * True while a player is close enough to watch this settlement. Work that people do with
     * their bodies there (jobs) is then reported by the mc layer as it visibly happens, and the
     * Ledger-tier job rules skip it so that it is not counted twice (rule E3).
     */
    public boolean embodied(String settlementId) { return embodied.contains(settlementId); }

    public void setEmbodied(String settlementId, boolean on) {
        if (on) embodied.add(settlementId); else embodied.remove(settlementId);
    }

    // ---- writers: EffectApplier, save loading and test fixtures only ----

    public void putCharacter(Character c) { characters.put(c.id(), c); }
    public void putSettlement(Settlement s) { settlements.put(s.id(), s); }
    public void putNation(Nation n) { nations.put(n.id(), n); }
    public void putStockpile(Stockpile s) { stockpiles.put(s.id(), s); }

    public void setStat(Ref ref, String key, double value) {
        stats.computeIfAbsent(ref.toString(), k -> new TreeMap<>()).put(key, value);
    }

    public void setTag(Ref ref, String key, String value) {
        Map<String, String> m = tags.computeIfAbsent(ref.toString(), k -> new TreeMap<>());
        if (value.isEmpty()) m.remove(key); else m.put(key, value);
    }

    // ---- saving ----

    /** The whole state as text, one record per line. {@link #load} reads it back. */
    public String save() {
        StringBuilder sb = new StringBuilder();
        sb.append(Wire.line("roe-state", 1, seed, hour)).append('\n');
        characters.values().forEach(c -> sb.append(Wire.line("C", c.encode())).append('\n'));
        settlements.values().forEach(s -> sb.append(Wire.line("S", s.encode())).append('\n'));
        nations.values().forEach(n -> sb.append(Wire.line("N", n.encode())).append('\n'));
        stockpiles.values().forEach(s -> sb.append(Wire.line("P", s.encode())).append('\n'));
        stats.forEach((ref, m) -> m.forEach((k, v) -> sb.append(Wire.line("V", ref, k, v)).append('\n')));
        tags.forEach((ref, m) -> m.forEach((k, v) -> sb.append(Wire.line("T", ref, k, v)).append('\n')));
        return sb.toString();
    }

    public static WorldState load(String text) {
        String[] lines = text.split("\n");
        String[] head = Wire.fields(lines[0]);
        if (!head[0].equals("roe-state")) throw new IllegalArgumentException("Not a Rise of Empires save");
        WorldState state = new WorldState(Long.parseLong(head[2]));
        state.hour = Long.parseLong(head[3]);
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isEmpty()) continue;
            String[] f = Wire.fields(lines[i]);
            switch (f[0]) {
                case "C" -> state.putCharacter(Character.decode(f[1]));
                case "S" -> state.putSettlement(Settlement.decode(f[1]));
                case "N" -> state.putNation(Nation.decode(f[1]));
                case "P" -> state.putStockpile(Stockpile.decode(f[1]));
                case "V" -> state.setStat(Ref.parse(f[1]), f[2], Double.parseDouble(f[3]));
                case "T" -> state.setTag(Ref.parse(f[1]), f[2], f[3]);
                default -> throw new IllegalArgumentException("Unknown save line: " + f[0]);
            }
        }
        return state;
    }
}
