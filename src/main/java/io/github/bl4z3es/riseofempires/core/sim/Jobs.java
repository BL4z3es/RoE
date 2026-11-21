package io.github.bl4z3es.riseofempires.core.sim;

import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Profession;
import io.github.bl4z3es.riseofempires.core.model.SimState;

/** The ruler gives a person a job. */
public final class Jobs {
	private Jobs() {}

	public static Event assign(SimState s, long characterId, Profession p, String byPlayer, long now) {
		Character c = s.character(characterId).orElseThrow();
		String me = Long.toString(characterId);
		EventDraft d = EventDraft.of(EventTypes.JOB_ASSIGNED, now)
				.actor(Ref.player(byPlayer)).actor(Ref.character(characterId))
				.effect(Ref.character(characterId), "profession", c.profession() + "->" + p);
		for (Event e : s.ledger().all()) {
			if (e.type().equals(EventTypes.SURVIVOR_JOINED)
					&& e.actors().stream().anyMatch(a -> a.kind().equals(Ref.CHARACTER) && a.id().equals(me))) {
				d.cause(e.id());
				break;
			}
		}
		Event e = s.ledger().append(d);
		s.put(c.withProfession(p), e);
		return e;
	}
}
