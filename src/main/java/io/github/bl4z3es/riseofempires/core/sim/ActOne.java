package io.github.bl4z3es.riseofempires.core.sim;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.ActOutcome;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import io.github.bl4z3es.riseofempires.core.model.Stockpile;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToIntFunction;

/** Act I: survive the first winter (CONTEXT.md 2). */
public final class ActOne {
	private ActOne() {}

	private static final Set<String> EXPLAINED_TYPES = Set.of(
			EventTypes.CHARACTER_DIED, EventTypes.CHARACTER_LEFT, EventTypes.MEAL_MISSED,
			EventTypes.CONDITION_CHANGED, EventTypes.GOODS_STOLEN, EventTypes.RAID_ENDED,
			EventTypes.WINTER_WARNING, EventTypes.SEASON_CHANGED, EventTypes.MEAL_EATEN,
			EventTypes.SETTLEMENT_FOUNDED);

	/** The first winter that begins at or after the founding. */
	public static long winterIndex(long foundedTime) {
		long i = Math.max(0, Calendar.seasonIndex(foundedTime));
		while (Calendar.seasonOfIndex(i) != Season.WINTER || Calendar.seasonStart(i) < foundedTime) i++;
		return i;
	}

	/** Quarter-meals a settlement of this many people needs to eat through one winter. */
	static int winterNeedQuarters(int residents) {
		return residents * Balance.MEAL_TIMES_OF_DAY.length * Balance.SEASON_LENGTH_DAYS
				* (int) Math.round(Balance.QUARTERS_PER_MEAL * Balance.WINTER_FOOD_MULTIPLIER);
	}

	public static List<Event> update(SimState s, long now, ToIntFunction<Stockpile> foodQuarters) {
		List<Event> out = new ArrayList<>();
		for (Settlement st : new ArrayList<>(s.settlements())) {
			if (s.actOne(st.id()) != ActOutcome.UNDECIDED) continue;
			long sid = st.id();
			Ref settlementRef = Ref.settlement(sid);
			long foundedTime = s.ledger().get(st.foundedEventId()).map(Event::time).orElse(0L);
			long winter = winterIndex(foundedTime);
			List<Character> living = s.residents(sid);

			// a. Warning one season ahead
			if (Calendar.seasonIndex(now) >= winter - 1 && s.fact("winter_warned." + sid).isEmpty() && !living.isEmpty()) {
				long cause = factId(s, "season.event." + (winter - 1)).orElse(st.foundedEventId());
				Event e = s.ledger().append(EventDraft.of(EventTypes.WINTER_WARNING, now).at(st.centre())
						.actor(Ref.character(living.get(0).id())).actor(settlementRef)
						.effect(settlementRef, "food_needed", Integer.toString(winterNeedQuarters(living.size())))
						.effect(settlementRef, "food_stored", Integer.toString(foodQuarters.applyAsInt(s.stockpile(sid))))
						.cause(cause)
						.witnesses(living.stream().map(c -> Ref.character(c.id())).toList()));
				s.putFact("winter_warned." + sid, Long.toString(e.id()), e);
				out.add(e);
			}

			// b. Lost
			if (!st.population().isEmpty() && living.isEmpty()) {
				EventDraft d = EventDraft.of(EventTypes.ACT_ONE_ENDED, now).at(st.centre())
						.actor(settlementRef).effect(settlementRef, "outcome", "LOST");
				boolean any = false;
				for (long personId : st.population()) {
					Optional<Event> gone = latestGone(s, personId);
					if (gone.isPresent()) { d.cause(gone.get().id()); any = true; }
				}
				if (!any) d.cause(st.foundedEventId());
				Event e = s.ledger().append(d);
				s.putActOne(sid, ActOutcome.LOST, e);
				out.add(e);
				continue;
			}

			// c. Survived
			if (now >= Calendar.seasonStart(winter + 1) && !living.isEmpty()) {
				EventDraft d = EventDraft.of(EventTypes.ACT_ONE_ENDED, now).at(st.centre())
						.actor(settlementRef).effect(settlementRef, "outcome", "SURVIVED");
				d.cause(st.foundedEventId());
				factId(s, "season.event." + (winter + 1)).ifPresent(d::cause);
				factId(s, "winter_warned." + sid).ifPresent(d::cause);
				Raids.latestFor(s, EventTypes.MEAL_EATEN, settlementRef).ifPresent(e -> d.cause(e.id()));
				Event e = s.ledger().append(d);
				s.putActOne(sid, ActOutcome.SURVIVED, e);
				out.add(e);
			}
		}
		return out;
	}

	/** The act-ending event and the events that led to it, oldest first after it. */
	public static List<Event> explain(SimState s, long settlementId) {
		Optional<Event> ended = Raids.latestFor(s, EventTypes.ACT_ONE_ENDED, Ref.settlement(settlementId));
		if (ended.isEmpty()) return List.of();
		List<Event> out = new ArrayList<>();
		out.add(ended.get());
		s.ledger().causeChain(ended.get().id()).stream()
				.filter(e -> EXPLAINED_TYPES.contains(e.type()))
				.sorted((a, b) -> Long.compare(a.id(), b.id()))
				.forEach(out::add);
		return out;
	}

	private static Optional<Long> factId(SimState s, String key) {
		return s.fact(key).map(Long::parseLong);
	}

	private static Optional<Event> latestGone(SimState s, long characterId) {
		Ref ref = Ref.character(characterId);
		List<Event> all = s.ledger().all();
		for (int i = all.size() - 1; i >= 0; i--) {
			Event e = all.get(i);
			if ((e.type().equals(EventTypes.CHARACTER_DIED) || e.type().equals(EventTypes.CHARACTER_LEFT))
					&& (e.actors().contains(ref) || e.effects().stream().anyMatch(f -> f.target().equals(ref)))) {
				return Optional.of(e);
			}
		}
		return Optional.empty();
	}
}
