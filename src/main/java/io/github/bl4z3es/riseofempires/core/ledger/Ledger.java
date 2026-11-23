package io.github.bl4z3es.riseofempires.core.ledger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The append-only log of everything that happened (CONTEXT.md 3.11).
 *
 * <p>Ids count up from 1. An event may only name causes that are already in the log, so every cause
 * chain ends at real root events and can never loop.
 */
public final class Ledger {
	private final List<Event> events = new ArrayList<>();

	/** Gives the draft the next id, checks its causes and appends it. */
	public Event append(EventDraft draft) {
		long id = events.size() + 1L;
		for (long cause : draft.causes) {
			if (cause < 1 || cause >= id) {
				throw new IllegalArgumentException("Event " + draft.type + " names cause " + cause
						+ ", which is not in the log (last id is " + (id - 1) + ")");
			}
		}
		Event event = draft.build(id);
		events.add(event);
		return event;
	}

	/**
	 * Adds an event read back from storage. Its id must be the next one in sequence and its causes must
	 * already be loaded.
	 */
	public void restore(Event event) {
		long expected = events.size() + 1L;
		if (event.id() != expected) {
			throw new IllegalArgumentException("Expected event " + expected + " but read " + event.id());
		}
		for (long cause : event.causes()) {
			if (cause < 1 || cause >= event.id()) {
				throw new IllegalArgumentException("Event " + event.id() + " names missing cause " + cause);
			}
		}
		events.add(event);
	}

	public Optional<Event> get(long id) {
		if (id < 1 || id > events.size()) return Optional.empty();
		return Optional.of(events.get((int) (id - 1)));
	}

	public boolean contains(Event event) {
		return get(event.id()).map(e -> e == event || e.equals(event)).orElse(false);
	}

	public int size() { return events.size(); }

	/** Id of the newest event, or 0 when the log is empty. */
	public long lastId() { return events.size(); }

	/** All events, oldest first. The list cannot be changed. */
	public List<Event> all() { return Collections.unmodifiableList(events); }

	/** Events with an id greater than {@code afterId}, oldest first. */
	public List<Event> after(long afterId) {
		int from = (int) Math.max(0, Math.min(afterId, events.size()));
		return Collections.unmodifiableList(events.subList(from, events.size()));
	}

	/** The newest {@code n} events, newest first. */
	public List<Event> latest(int n) {
		List<Event> out = new ArrayList<>();
		for (int i = events.size() - 1; i >= 0 && out.size() < n; i--) out.add(events.get(i));
		return out;
	}

	/** Newest event of a type, if any. */
	public Optional<Event> latestOfType(String type) {
		for (int i = events.size() - 1; i >= 0; i--) {
			if (events.get(i).type().equals(type)) return Optional.of(events.get(i));
		}
		return Optional.empty();
	}

	/**
	 * Everything that led to an event: its causes, their causes and so on, each listed once,
	 * nearest first. The event itself is not included.
	 */
	public List<Event> causeChain(long id) {
		Event start = get(id).orElseThrow(() -> new IllegalArgumentException("No event " + id));
		List<Event> out = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		Deque<Long> queue = new ArrayDeque<>(start.causes());
		while (!queue.isEmpty()) {
			long next = queue.removeFirst();
			if (!seen.add(next)) continue;
			Event e = get(next).orElseThrow();
			out.add(e);
			queue.addAll(e.causes());
		}
		return out;
	}

	/** The root events a chain starts from. An event with no causes is its own root. */
	public List<Event> roots(long id) {
		Event start = get(id).orElseThrow(() -> new IllegalArgumentException("No event " + id));
		if (start.isRoot()) return List.of(start);
		List<Event> out = new ArrayList<>();
		for (Event e : causeChain(id)) if (e.isRoot()) out.add(e);
		out.sort((a, b) -> Long.compare(a.id(), b.id()));
		return out;
	}
}
