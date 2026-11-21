package io.github.bl4z3es.riseofempires.mc.command;

import io.github.bl4z3es.riseofempires.core.ledger.Effect;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import io.github.bl4z3es.riseofempires.core.sim.Calendar;
import java.util.stream.Collectors;

/** Plain-text descriptions of events and references, for debug commands. */
public final class Describe {
	private Describe() {}

	public static String when(long time) {
		return "day " + (Calendar.day(time) + 1) + ", " + Calendar.season(time).name().toLowerCase()
				+ " " + Calendar.dayOfSeason(time) + ", year " + Calendar.year(time);
	}

	public static String ref(SimState s, Ref r) {
		try {
			return switch (r.kind()) {
				case Ref.CHARACTER -> s.character(Long.parseLong(r.id())).map(c -> c.name()).orElse(r.toString());
				case Ref.SETTLEMENT -> s.settlement(Long.parseLong(r.id())).map(x -> x.name()).orElse(r.toString());
				case Ref.RAID -> "raid " + r.id();
				case Ref.PLAYER -> "a player";
				case Ref.WORLD -> "the world";
				default -> r.toString();
			};
		} catch (NumberFormatException e) {
			return r.toString();
		}
	}

	/** One line: id, type, when, who. */
	public static String line(SimState s, Event e) {
		String who = e.actors().stream().map(r -> ref(s, r)).collect(Collectors.joining(", "));
		return "#" + e.id() + " " + e.type() + " (" + when(e.time()) + ")" + (who.isEmpty() ? "" : " - " + who);
	}

	/** The line plus its effects and causes. */
	public static String full(SimState s, Event e) {
		StringBuilder b = new StringBuilder(line(s, e));
		for (Effect f : e.effects()) b.append("\n    ").append(ref(s, f.target())).append(": ").append(f.what()).append(' ').append(f.detail());
		if (!e.causes().isEmpty()) {
			b.append("\n    because of ").append(e.causes().stream().map(c -> "#" + c).collect(Collectors.joining(", ")));
		}
		return b.toString();
	}
}
