package io.github.bl4z3es.riseofempires.core.ledger;

import java.util.ArrayList;
import java.util.List;

/** Builder for an event that has not happened yet. The Ledger gives it an id and a time. */
public final class EventDraft {
    final String type;
    Place place = Place.NOWHERE;
    String summary = "";
    final List<Ref> actors = new ArrayList<>();
    final List<Long> causes = new ArrayList<>();
    final List<Effect> effects = new ArrayList<>();
    final List<Ref> witnesses = new ArrayList<>();

    private EventDraft(String type) {
        this.type = type;
    }

    public static EventDraft of(String type) {
        return new EventDraft(type);
    }

    public EventDraft at(Place place) { this.place = place; return this; }
    public EventDraft summary(String summary) { this.summary = summary; return this; }
    public EventDraft actor(Ref ref) { actors.add(ref); return this; }
    public EventDraft witness(Ref ref) { witnesses.add(ref); return this; }
    public EventDraft effect(Effect effect) { effects.add(effect); return this; }

    /** Links a cause. Ids of 0 or less are ignored so callers can pass "no known cause". */
    public EventDraft cause(long eventId) {
        if (eventId > 0 && !causes.contains(eventId)) causes.add(eventId);
        return this;
    }

    public EventDraft causedBy(Event event) {
        return event == null ? this : cause(event.id());
    }

    public String type() { return type; }
    public List<Effect> effects() { return effects; }
}
