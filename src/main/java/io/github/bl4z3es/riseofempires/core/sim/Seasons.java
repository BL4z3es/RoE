package io.github.bl4z3es.riseofempires.core.sim;

import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import java.util.ArrayList;
import java.util.List;

/** Writes one Ledger event for every season boundary the world has crossed (CONTEXT.md 3.5). */
public final class Seasons {
	private Seasons() {}

	private static final String CURSOR = "season";
	private static final String WORLD_BEGAN_FACT = "world_began";

	public static List<Event> update(SimState s, long now) {
		List<Event> out = new ArrayList<>();
		if (s.fact(WORLD_BEGAN_FACT).isEmpty()) {
			Event began = s.ledger().append(EventDraft.of(EventTypes.WORLD_BEGAN, 0).actor(Ref.world()));
			s.putFact(WORLD_BEGAN_FACT, Long.toString(began.id()), began);
			out.add(began);
		}
		long current = Calendar.seasonIndex(now);
		if (!s.cursors().containsKey(CURSOR)) {
			s.setCursor(CURSOR, current);
			return out;
		}
		for (long i = s.cursor(CURSOR, current) + 1; i <= current; i++) {
			long time = Calendar.seasonStart(i);
			long cause = s.ledger().latestOfType(EventTypes.SEASON_CHANGED).map(Event::id)
					.orElse(Long.parseLong(s.fact(WORLD_BEGAN_FACT).orElseThrow()));
			Season season = Calendar.seasonOfIndex(i);
			String name = season.name().charAt(0) + season.name().substring(1).toLowerCase();
			Event e = s.ledger().append(EventDraft.of(EventTypes.SEASON_CHANGED, time)
					.actor(Ref.world())
					.effect(Ref.world(), "season", name + " of year " + Calendar.year(time))
					.cause(cause));
			s.putFact("season.event." + i, Long.toString(e.id()), e);
			s.setCursor(CURSOR, i);
			out.add(e);
		}
		return out;
	}
}
