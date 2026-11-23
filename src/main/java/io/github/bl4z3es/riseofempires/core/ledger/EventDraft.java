package io.github.bl4z3es.riseofempires.core.ledger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** An event before the {@link Ledger} gives it an id. Build one, then pass it to {@link Ledger#append}. */
public final class EventDraft {
	final String type;
	final long time;
	Place place;
	final List<Ref> actors = new ArrayList<>();
	final List<Long> causes = new ArrayList<>();
	final List<Effect> effects = new ArrayList<>();
	final List<Ref> witnesses = new ArrayList<>();

	private EventDraft(String type, long time) {
		this.type = Objects.requireNonNull(type, "type");
		this.time = time;
	}

	public static EventDraft of(String type, long time) { return new EventDraft(type, time); }

	public EventDraft at(Place place) { this.place = place; return this; }
	public EventDraft actor(Ref ref) { actors.add(ref); return this; }
	public EventDraft actors(List<Ref> refs) { actors.addAll(refs); return this; }
	public EventDraft cause(long eventId) { if (!causes.contains(eventId)) causes.add(eventId); return this; }
	public EventDraft causes(List<Long> eventIds) { eventIds.forEach(this::cause); return this; }
	public EventDraft effect(Ref target, String what, String detail) { effects.add(new Effect(target, what, detail)); return this; }
	public EventDraft witness(Ref ref) { witnesses.add(ref); return this; }
	public EventDraft witnesses(List<Ref> refs) { witnesses.addAll(refs); return this; }

	Event build(long id) { return new Event(id, time, place, type, actors, causes, effects, witnesses); }
}
