package io.github.bl4z3es.riseofempires.core.ledger;

import java.util.List;
import java.util.Objects;

/**
 * One thing that happened, with what caused it (CONTEXT.md 3.11, rule E2).
 *
 * @param id        assigned by the {@link Ledger}; ids count up from 1 in the order events were written
 * @param time      world time in ticks when it happened
 * @param place     where it happened, or {@code null} if it has no single place (a season change)
 * @param type      what kind of event, one of {@link EventTypes}
 * @param actors    who did it or had it happen to them
 * @param causes    ids of the earlier events that led here
 * @param effects   the state changes it applied
 * @param witnesses who saw it
 */
public record Event(
		long id,
		long time,
		Place place,
		String type,
		List<Ref> actors,
		List<Long> causes,
		List<Effect> effects,
		List<Ref> witnesses) {
	public Event {
		Objects.requireNonNull(type, "type");
		actors = List.copyOf(actors);
		causes = List.copyOf(causes);
		effects = List.copyOf(effects);
		witnesses = List.copyOf(witnesses);
	}

	/** True if this event has no recorded causes: the start of a chain. */
	public boolean isRoot() { return causes.isEmpty(); }
}
