package io.github.bl4z3es.riseofempires.core.model;

import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.Ledger;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Everything the simulation knows, plus the Ledger that explains it.
 *
 * <p>Rule E2: every game-state change names the logged {@link Event} that caused it. The {@code put}
 * and {@code remove} methods refuse an event that is not in this state's Ledger. Cursors (which meal
 * slot or hour was last processed) are bookkeeping, not game state, and change without an event.
 *
 * <p>Maps are sorted by id so iteration order, and therefore the simulation, is deterministic (rule E4).
 */
public final class SimState {
	private final long seed;
	private final Ledger ledger;
	private final TreeMap<Long, Character> characters = new TreeMap<>();
	private final TreeMap<Long, Settlement> settlements = new TreeMap<>();
	private final TreeMap<Long, Stockpile> stockpiles = new TreeMap<>();
	private final TreeMap<Long, Raid> raids = new TreeMap<>();
	private final TreeMap<Long, BuildSite> buildSites = new TreeMap<>();
	private final TreeMap<Long, List<Plot>> farms = new TreeMap<>();
	private final TreeMap<Long, ActOutcome> actOne = new TreeMap<>();
	private final TreeMap<String, Long> foundedBy = new TreeMap<>();
	private final TreeMap<String, String> facts = new TreeMap<>();
	private final TreeMap<String, Long> counters = new TreeMap<>();
	private final TreeMap<String, Long> cursors = new TreeMap<>();

	public SimState(long seed) { this(seed, new Ledger()); }

	public SimState(long seed, Ledger ledger) {
		this.seed = seed;
		this.ledger = ledger;
	}

	public long seed() { return seed; }
	public Ledger ledger() { return ledger; }

	private void requireLogged(Event because) {
		if (because == null || !ledger.contains(because)) {
			throw new IllegalStateException("State change without a logged event (rule E2)");
		}
	}

	// Ids

	/** Next id for a kind of record (character, settlement, raid, site). Ids start at 1 and never repeat. */
	public long nextId(String kind) {
		long next = counters.getOrDefault(kind, 0L) + 1;
		counters.put(kind, next);
		return next;
	}

	public Map<String, Long> counters() { return Collections.unmodifiableMap(counters); }
	public void restoreCounter(String kind, long value) { counters.put(kind, value); }

	// Characters

	public Optional<Character> character(long id) { return Optional.ofNullable(characters.get(id)); }
	public Collection<Character> characters() { return Collections.unmodifiableCollection(characters.values()); }
	public void put(Character c, Event because) { requireLogged(because); characters.put(c.id(), c); }

	// Settlements

	public Optional<Settlement> settlement(long id) { return Optional.ofNullable(settlements.get(id)); }
	public Collection<Settlement> settlements() { return Collections.unmodifiableCollection(settlements.values()); }
	public void put(Settlement s, Event because) { requireLogged(because); settlements.put(s.id(), s); }

	public Optional<Long> settlementFoundedBy(String player) { return Optional.ofNullable(foundedBy.get(player)); }
	public Map<String, Long> foundedBy() { return Collections.unmodifiableMap(foundedBy); }
	public void recordFounder(String player, long settlementId, Event because) { requireLogged(because); foundedBy.put(player, settlementId); }

	/** Living residents of a settlement, in the order they joined. */
	public List<Character> residents(long settlementId) {
		Settlement s = settlements.get(settlementId);
		if (s == null) return List.of();
		return s.population().stream().map(characters::get)
				.filter(c -> c != null && !c.condition().isGone()).toList();
	}

	// Stockpiles

	public Stockpile stockpile(long settlementId) { return stockpiles.getOrDefault(settlementId, Stockpile.empty(settlementId)); }
	public Collection<Stockpile> stockpiles() { return Collections.unmodifiableCollection(stockpiles.values()); }
	public void put(Stockpile s, Event because) { requireLogged(because); stockpiles.put(s.settlementId(), s); }

	// Raids

	public Optional<Raid> raid(long id) { return Optional.ofNullable(raids.get(id)); }
	public Collection<Raid> raids() { return Collections.unmodifiableCollection(raids.values()); }
	public void put(Raid r, Event because) { requireLogged(because); raids.put(r.id(), r); }

	// Build sites

	public Optional<BuildSite> buildSite(long id) { return Optional.ofNullable(buildSites.get(id)); }
	public Collection<BuildSite> buildSites() { return Collections.unmodifiableCollection(buildSites.values()); }
	public void put(BuildSite b, Event because) { requireLogged(because); buildSites.put(b.id(), b); }

	// Farms

	public List<Plot> farm(long settlementId) { return farms.getOrDefault(settlementId, List.of()); }
	public Map<Long, List<Plot>> farms() { return Collections.unmodifiableMap(farms); }
	public void putFarm(long settlementId, List<Plot> plots, Event because) { requireLogged(because); farms.put(settlementId, List.copyOf(plots)); }

	// Act I

	public ActOutcome actOne(long settlementId) { return actOne.getOrDefault(settlementId, ActOutcome.UNDECIDED); }
	public Map<Long, ActOutcome> actOneOutcomes() { return Collections.unmodifiableMap(actOne); }
	public void putActOne(long settlementId, ActOutcome outcome, Event because) { requireLogged(because); actOne.put(settlementId, outcome); }

	// World facts: named values such as where the Hollow Ruin stands

	public Optional<String> fact(String key) { return Optional.ofNullable(facts.get(key)); }
	public Map<String, String> facts() { return Collections.unmodifiableMap(facts); }
	public void putFact(String key, String value, Event because) { requireLogged(because); facts.put(key, value); }

	// Cursors: bookkeeping for "processed up to", not game state

	public long cursor(String key, long fallback) { return cursors.getOrDefault(key, fallback); }
	public Map<String, Long> cursors() { return Collections.unmodifiableMap(cursors); }
	public void setCursor(String key, long value) { cursors.put(key, value); }

	// Stock history: the newest event that changed each settlement's stockpile, so later events
	// (a missed meal, a theft) can name it as a cause.

	public long lastStockEvent(long settlementId) { return cursors.getOrDefault("stock." + settlementId, 0L); }
	public void noteStockEvent(long settlementId, Event e) { requireLogged(e); cursors.put("stock." + settlementId, e.id()); }

	// Restore: used only by StateCodec when loading a save. Loading replays no events, so it skips E2.

	void restore(Character c) { characters.put(c.id(), c); }
	void restore(Settlement s) { settlements.put(s.id(), s); }
	void restore(Stockpile s) { stockpiles.put(s.settlementId(), s); }
	void restore(Raid r) { raids.put(r.id(), r); }
	void restore(BuildSite b) { buildSites.put(b.id(), b); }
	void restoreFarm(long settlementId, List<Plot> plots) { farms.put(settlementId, List.copyOf(plots)); }
	void restoreActOne(long settlementId, ActOutcome o) { actOne.put(settlementId, o); }
	void restoreFounder(String player, long settlementId) { foundedBy.put(player, settlementId); }
	void restoreFact(String key, String value) { facts.put(key, value); }
}
