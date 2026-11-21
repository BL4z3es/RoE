package io.github.bl4z3es.riseofempires.core.sim;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.ActOutcome;
import io.github.bl4z3es.riseofempires.core.model.Raid;
import io.github.bl4z3es.riseofempires.core.model.RaidStage;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import io.github.bl4z3es.riseofempires.core.model.Stockpile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;

/** The life cycle of a T1 raid: planned, sighted, arrived, ended (CONTEXT.md 2). */
public final class Raids {
	private Raids() {}

	public static List<Event> update(SimState s, long now) {
		List<Event> out = new ArrayList<>();
		for (Settlement st : new ArrayList<>(s.settlements())) plan(s, st, now, out);
		for (Raid raid : new ArrayList<>(s.raids())) advance(s, raid, now, out);
		return out;
	}

	private static List<Ref> residentRefs(SimState s, long sid) {
		return s.residents(sid).stream().map(c -> Ref.character(c.id())).toList();
	}

	private static void plan(SimState s, Settlement st, long now, List<Event> out) {
		long sid = st.id();
		if (s.actOne(sid) != ActOutcome.UNDECIDED || s.fact("raid." + sid).isPresent()) return;
		if (s.residents(sid).isEmpty()) return;
		Season season = Calendar.season(now);
		if (season != Season.AUTUMN && season != Season.WINTER) return;
		long foundedTime = s.ledger().get(st.foundedEventId()).map(Event::time).orElse(0L);
		if (Calendar.day(now) - Calendar.day(foundedTime) < Balance.RAID_EARLIEST_DAY_AFTER_FOUNDING) return;

		long size = Math.round(Balance.T1_RAID_BASE * Standing.of(s, sid).total());
		int clamped = (int) Math.max(Balance.T1_RAID_SIZE_MIN, Math.min(Balance.T1_RAID_SIZE_MAX, size));
		double angle = Dice.of(s.seed(), sid, 7).nextDouble() * 2 * Math.PI;
		Place c = st.centre();
		Place origin = new Place(c.dimension(),
				c.x() + (int) Math.round(Balance.RAID_ORIGIN_DISTANCE_BLOCKS * Math.cos(angle)), c.y(),
				c.z() + (int) Math.round(Balance.RAID_ORIGIN_DISTANCE_BLOCKS * Math.sin(angle)));
		long sightTime = now + Balance.RAID_SIGHT_DELAY_TICKS;
		long arriveTime = sightTime + Balance.RAID_ARRIVAL_DELAY_TICKS;
		long raidId = s.nextId("raid");
		Ref raidRef = Ref.raid(raidId);

		EventDraft d = EventDraft.of(EventTypes.RAID_PLANNED, now).at(origin)
				.actor(raidRef).actor(Ref.settlement(sid))
				.effect(raidRef, "size", Integer.toString(clamped))
				.effect(raidRef, "arrives_at", Long.toString(arriveTime))
				.cause(st.foundedEventId());
		s.ledger().latestOfType(EventTypes.SEASON_CHANGED).ifPresent(e -> d.cause(e.id()));
		long stock = s.lastStockEvent(sid);
		if (stock > 0) d.cause(stock);
		Event planned = s.ledger().append(d);
		s.put(new Raid(raidId, sid, clamped, planned.id(), origin, sightTime, arriveTime, RaidStage.PLANNED, clamped), planned);
		s.putFact("raid." + sid, Long.toString(raidId), planned);
		out.add(planned);
	}

	private static void advance(SimState s, Raid raid, long now, List<Event> out) {
		Settlement st = s.settlement(raid.settlementId()).orElse(null);
		if (st == null) return;
		Ref raidRef = Ref.raid(raid.id());
		if (raid.stage() == RaidStage.PLANNED && now >= raid.sightTime()) {
			Event e = s.ledger().append(EventDraft.of(EventTypes.RAID_SIGHTED, raid.sightTime()).at(st.centre())
					.actor(raidRef).actor(Ref.settlement(st.id()))
					.effect(raidRef, "arrives_at", Long.toString(raid.arriveTime()))
					.cause(raid.plannedEventId())
					.witnesses(residentRefs(s, st.id())));
			raid = raid.withStage(RaidStage.SIGHTED);
			s.put(raid, e);
			out.add(e);
		}
		if (raid.stage() == RaidStage.SIGHTED && now >= raid.arriveTime()) {
			long cause = latestFor(s, EventTypes.RAID_SIGHTED, raidRef).map(Event::id).orElse(raid.plannedEventId());
			Event e = s.ledger().append(EventDraft.of(EventTypes.RAID_ARRIVED, raid.arriveTime()).at(st.centre())
					.actor(raidRef).actor(Ref.settlement(st.id())).cause(cause));
			s.put(raid.withStage(RaidStage.ARRIVED), e);
			out.add(e);
		}
	}

	public static List<Event> raiderKilled(SimState s, long raidId, Ref killer, long now) {
		Raid raid = s.raid(raidId).orElseThrow();
		Ref raidRef = Ref.raid(raidId);
		int left = Math.max(0, raid.raidersLeft() - 1);
		List<Event> out = new ArrayList<>();
		Event killed = s.ledger().append(EventDraft.of(EventTypes.RAIDER_KILLED, now).at(placeOf(s, raid))
				.actor(raidRef).actor(killer)
				.effect(raidRef, "raiders_left", Integer.toString(left))
				.cause(lastRaidEvent(s, raid)));
		raid = raid.withRaidersLeft(left);
		s.put(raid, killed);
		out.add(killed);
		if (left == 0) out.add(end(s, raid, "driven off", now, killed.id()));
		return out;
	}

	public static List<Event> stolen(SimState s, long raidId, Map<String, Integer> items, long now) {
		Raid raid = s.raid(raidId).orElseThrow();
		long sid = raid.settlementId();
		Map<String, Integer> taken = new TreeMap<>();
		items.forEach((k, v) -> { if (v > 0) taken.put(k, v); });
		List<Event> out = new ArrayList<>();
		long cause = lastRaidEvent(s, raid);
		if (!taken.isEmpty()) {
			Stockpile stock = s.stockpile(sid);
			Map<String, Integer> counted = new TreeMap<>(stock.counted());
			taken.forEach((k, v) -> counted.put(k, Math.max(0, counted.getOrDefault(k, 0) - v)));
			Event e = theft(s, raid, taken, now, cause);
			s.put(stock.withCounted(counted), e);
			s.noteStockEvent(sid, e);
			out.add(e);
			cause = e.id();
		}
		out.add(end(s, raid, "plundered", now, cause));
		return out;
	}

	public static List<Event> resolveUnwatched(SimState s, long raidId, Predicate<String> isFood, long now) {
		Raid raid = s.raid(raidId).orElseThrow();
		long sid = raid.settlementId();
		Stockpile stock = s.stockpile(sid);
		Map<String, Integer> taken = new TreeMap<>();
		for (Map.Entry<String, Integer> en : stock.totals().entrySet()) {
			if (!isFood.test(en.getKey())) continue;
			int n = (int) Math.floor(en.getValue() * Balance.RAID_STEAL_FRACTION);
			if (n > 0) taken.put(en.getKey(), n);
		}
		List<Event> out = new ArrayList<>();
		long cause = lastRaidEvent(s, raid);
		if (!taken.isEmpty()) {
			Event e = theft(s, raid, taken, now, cause);
			Stockpile next = stock;
			for (Map.Entry<String, Integer> en : taken.entrySet()) next = next.withPendingChange(en.getKey(), -en.getValue());
			s.put(next, e);
			s.noteStockEvent(sid, e);
			out.add(e);
			cause = e.id();
		}
		out.add(end(s, raid, "plundered", now, cause));
		return out;
	}

	private static Event theft(SimState s, Raid raid, Map<String, Integer> taken, long now, long cause) {
		Ref raidRef = Ref.raid(raid.id());
		Ref settlementRef = Ref.settlement(raid.settlementId());
		EventDraft d = EventDraft.of(EventTypes.GOODS_STOLEN, now).at(placeOf(s, raid))
				.actor(raidRef).actor(settlementRef).cause(cause)
				.witnesses(residentRefs(s, raid.settlementId()));
		taken.forEach((item, n) -> d.effect(settlementRef, item, Integer.toString(-n)));
		return s.ledger().append(d);
	}

	private static Event end(SimState s, Raid raid, String outcome, long now, long cause) {
		Ref raidRef = Ref.raid(raid.id());
		Event e = s.ledger().append(EventDraft.of(EventTypes.RAID_ENDED, now).at(placeOf(s, raid))
				.actor(raidRef).actor(Ref.settlement(raid.settlementId()))
				.effect(raidRef, "outcome", outcome).cause(cause));
		s.put(raid.withStage(RaidStage.ENDED), e);
		return e;
	}

	private static Place placeOf(SimState s, Raid raid) {
		return s.settlement(raid.settlementId()).map(Settlement::centre).orElse(raid.origin());
	}

	/** The newest of this raid's arrived, sighted or planned events. */
	private static long lastRaidEvent(SimState s, Raid raid) {
		Ref r = Ref.raid(raid.id());
		for (String type : new String[] {EventTypes.RAID_ARRIVED, EventTypes.RAID_SIGHTED}) {
			Optional<Event> e = latestFor(s, type, r);
			if (e.isPresent()) return e.get().id();
		}
		return raid.plannedEventId();
	}

	/** Newest event of a type that names the ref among its actors. */
	static Optional<Event> latestFor(SimState s, String type, Ref actor) {
		List<Event> all = s.ledger().all();
		for (int i = all.size() - 1; i >= 0; i--) {
			Event e = all.get(i);
			if (e.type().equals(type) && e.actors().contains(actor)) return Optional.of(e);
		}
		return Optional.empty();
	}
}
