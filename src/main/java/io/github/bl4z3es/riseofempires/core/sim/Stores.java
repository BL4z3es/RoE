package io.github.bl4z3es.riseofempires.core.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeMap;

import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Plot;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import io.github.bl4z3es.riseofempires.core.model.Stockpile;

/** The Granary Seal and the counting of what is stored. */
public final class Stores {
	private Stores() {}

	/** Goods leave the store. Not in EventTypes, which this class does not own. */
	public static final String GOODS_TAKEN = "goods_taken";

	public static Event sealGranary(SimState s, long settlementId, Place where, String byPlayer, long now) {
		Settlement st = s.settlement(settlementId).orElseThrow();
		Event e = s.ledger().append(EventDraft.of(EventTypes.GRANARY_SEALED, now).at(where)
				.actor(Ref.player(byPlayer)).actor(Ref.settlement(settlementId))
				.cause(st.foundedEventId())
				.effect(Ref.settlement(settlementId), "granary", where.toString()));
		s.put(st.withGranary(where), e);
		s.noteStockEvent(settlementId, e);
		return e;
	}

	/**
	 * The seal was removed. No event and no state change for a place that is not this camp's granary.
	 * The world layer first tries to write pending changes into the real chests; whatever it could not
	 * apply is dropped here and named in the event, rather than silently surviving without a store.
	 * Existing items in the chests stay there; only the settlement's counted store is cleared.
	 *
	 * @param by player, character or null when the game does not know who removed the seal
	 * @param how how the seal was removed: broken, exploded, piston or replaced
	 */
	public static Optional<Event> unsealGranary(SimState s, long settlementId, Place sealPlace, Ref by,
			String how, long now) {
		Settlement st = s.settlement(settlementId).orElse(null);
		if (st == null || !Objects.equals(st.granary(), sealPlace)) return Optional.empty();
		Stockpile sp = s.stockpile(settlementId);
		long sealEventId = s.ledger().all().stream()
				.filter(e -> e.type().equals(EventTypes.GRANARY_SEALED) && sealPlace.equals(e.place())
						&& e.actors().contains(Ref.settlement(settlementId)))
				.mapToLong(Event::id).max().orElse(st.foundedEventId());
		EventDraft d = EventDraft.of(EventTypes.GRANARY_UNSEALED, now).at(sealPlace)
				.actor(Ref.settlement(settlementId)).cause(sealEventId)
				.effect(Ref.settlement(settlementId), "granary", "none")
				.effect(Ref.settlement(settlementId), "seal_removed", how)
				.effect(Ref.settlement(settlementId), "sealed_by_event", Long.toString(sealEventId))
				.effect(Ref.settlement(settlementId), "counted_stock", "cleared; items remain in chests");
		if (by != null) d.actor(by);
		if (!sp.pending().isEmpty()) {
			d.effect(Ref.settlement(settlementId), "pending_changes", "dropped_unapplied");
			sp.pending().forEach((item, delta) -> d.effect(Ref.settlement(settlementId), "dropped_pending " + item,
					Integer.toString(delta)));
		}
		Event e = s.ledger().append(d);
		s.put(st.withGranary(null), e);
		s.put(Stockpile.empty(settlementId), e);
		s.noteStockEvent(settlementId, e);
		return Optional.of(e);
	}

	/** The world layer counted the real containers. Writes only if something changed or pending was flushed. */
	public static Optional<Event> recount(SimState s, long settlementId, Map<String, Integer> count,
			boolean pendingFlushed, long now) {
		Settlement st = s.settlement(settlementId).orElseThrow();
		Stockpile sp = s.stockpile(settlementId);
		Map<String, Integer> fresh = positive(count);
		Map<String, Integer> old = positive(sp.counted());
		if (!pendingFlushed && fresh.equals(old)) return Optional.empty();
		EventDraft d = EventDraft.of(EventTypes.STOCKPILE_COUNTED, now).actor(Ref.settlement(settlementId))
				.cause(causeFor(s, st));
		TreeMap<String, Integer> keys = new TreeMap<>(old);
		keys.putAll(fresh);
		for (String item : keys.keySet()) {
			int delta = fresh.getOrDefault(item, 0) - old.getOrDefault(item, 0);
			if (delta != 0) d.effect(Ref.settlement(settlementId), item, (delta > 0 ? "+" : "") + delta);
		}
		Event e = s.ledger().append(d);
		s.put(pendingFlushed ? sp.reconciled(count) : sp.withCounted(count), e);
		s.noteStockEvent(settlementId, e);
		return Optional.of(e);
	}

	public static Event deposit(SimState s, long sid, long characterId, Map<String, Integer> items, long now) {
		Settlement st = s.settlement(sid).orElseThrow();
		EventDraft d = EventDraft.of(EventTypes.GOODS_DEPOSITED, now)
				.actor(Ref.character(characterId)).actor(Ref.settlement(sid)).cause(causeFor(s, st));
		items.forEach((k, v) -> d.effect(Ref.settlement(sid), k, "+" + v));
		Event e = s.ledger().append(d);
		Stockpile sp = s.stockpile(sid);
		TreeMap<String, Integer> next = new TreeMap<>(sp.counted());
		items.forEach((k, v) -> next.merge(k, v, Integer::sum));
		s.put(sp.withCounted(next), e);
		s.noteStockEvent(sid, e);
		return e;
	}

	public static Event take(SimState s, long sid, long characterId, Map<String, Integer> items, long now) {
		Settlement st = s.settlement(sid).orElseThrow();
		EventDraft d = EventDraft.of(GOODS_TAKEN, now)
				.actor(Ref.character(characterId)).actor(Ref.settlement(sid)).cause(causeFor(s, st));
		items.forEach((k, v) -> d.effect(Ref.settlement(sid), k, "-" + v));
		Event e = s.ledger().append(d);
		Stockpile sp = s.stockpile(sid);
		TreeMap<String, Integer> next = new TreeMap<>(sp.counted());
		items.forEach((k, v) -> next.merge(k, -v, Integer::sum));
		next.replaceAll((k, v) -> Math.max(0, v));
		s.put(sp.withCounted(next), e);
		s.noteStockEvent(sid, e);
		return e;
	}

	/** A crop was cut. The harvest is carried, so the stockpile does not change. */
	public static Event harvested(SimState s, long sid, long characterId, Place plot, Map<String, Integer> items, long now) {
		Settlement st = s.settlement(sid).orElseThrow();
		EventDraft d = EventDraft.of(EventTypes.CROP_HARVESTED, now).at(plot)
				.actor(Ref.character(characterId)).actor(Ref.settlement(sid)).cause(causeFor(s, st));
		items.forEach((k, v) -> d.effect(Ref.character(characterId), k, "+" + v));
		Event e = s.ledger().append(d);
		List<Plot> farm = s.farm(sid);
		if (farm.stream().anyMatch(p -> p.pos().equals(plot))) {
			List<Plot> next = new ArrayList<>();
			for (Plot p : farm) next.add(p.pos().equals(plot) ? p.withAge(0) : p);
			s.putFarm(sid, next, e);
		}
		return e;
	}

	/** Wood, stone or other goods gathered. Carried, so effects only. */
	public static Event gathered(SimState s, long sid, long characterId, Place where, Map<String, Integer> items, long now) {
		Settlement st = s.settlement(sid).orElseThrow();
		EventDraft d = EventDraft.of(EventTypes.GOODS_GATHERED, now).at(where)
				.actor(Ref.character(characterId)).actor(Ref.settlement(sid)).cause(causeFor(s, st));
		items.forEach((k, v) -> d.effect(Ref.character(characterId), k, "+" + v));
		return s.ledger().append(d);
	}

	private static long causeFor(SimState s, Settlement st) {
		long last = s.lastStockEvent(st.id());
		return last > 0 ? last : st.foundedEventId();
	}

	private static Map<String, Integer> positive(Map<String, Integer> m) {
		TreeMap<String, Integer> out = new TreeMap<>();
		m.forEach((k, v) -> { if (v > 0) out.put(k, v); });
		return out;
	}
}
