package io.github.bl4z3es.riseofempires.core.ledger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The append-only log of everything that happened. Ids start at 1 and rise by one, so the event
 * with id {@code n} is at index {@code n - 1}. The Ledger only records; applying effects to the
 * world is done by {@code core.sim.Simulation}, which is the only caller of {@link #append}
 * outside tests.
 */
public final class Ledger {
    private final List<Event> events = new ArrayList<>();
    private final List<Consumer<Event>> listeners = new ArrayList<>();

    public Event append(EventDraft draft, long hour) {
        for (long cause : draft.causes) {
            if (cause > events.size()) throw new IllegalArgumentException("Unknown cause " + cause);
        }
        Event event = new Event(events.size() + 1L, hour, draft.place, draft.type,
                draft.actors, draft.causes, draft.effects, draft.witnesses, draft.summary);
        events.add(event);
        for (Consumer<Event> listener : listeners) listener.accept(event);
        return event;
    }

    /** Adds an event that was saved earlier. Lines must be loaded in their original order. */
    public void restore(Event event) {
        if (event.id() != events.size() + 1L) {
            throw new IllegalStateException("Event log out of order at id " + event.id());
        }
        events.add(event);
    }

    /** Called after every append. The save layer uses this to write the new line to disk. */
    public void onAppend(Consumer<Event> listener) {
        listeners.add(listener);
    }

    public Event get(long id) {
        if (id < 1 || id > events.size()) return null;
        return events.get((int) id - 1);
    }

    public int size() {
        return events.size();
    }

    public List<Event> all() {
        return Collections.unmodifiableList(events);
    }

    /** The last {@code n} events, oldest first. */
    public List<Event> recent(int n) {
        return List.copyOf(events.subList(Math.max(0, events.size() - n), events.size()));
    }

    /** The most recent event of this type, or null. */
    public Event latest(String type) {
        for (int i = events.size() - 1; i >= 0; i--) {
            if (events.get(i).type().equals(type)) return events.get(i);
        }
        return null;
    }

    /**
     * Everything that led to this event: its causes, their causes, and so on, nearest first.
     * The event itself is not included. Shared ancestors appear once.
     */
    public List<Event> causeChain(long id) {
        Set<Long> seen = new LinkedHashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        Event start = get(id);
        if (start == null) return List.of();
        queue.addAll(start.causes());
        while (!queue.isEmpty()) {
            long next = queue.poll();
            if (seen.add(next)) queue.addAll(get(next).causes());
        }
        return seen.stream().map(this::get).toList();
    }

    /** The events in the cause chain that have no cause of their own. */
    public List<Event> roots(long id) {
        return causeChain(id).stream().filter(e -> e.causes().isEmpty()).toList();
    }
}
