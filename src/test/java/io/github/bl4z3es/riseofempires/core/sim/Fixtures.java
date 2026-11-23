package io.github.bl4z3es.riseofempires.core.sim;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Condition;
import io.github.bl4z3es.riseofempires.core.model.Needs;
import io.github.bl4z3es.riseofempires.core.model.Profession;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import java.util.List;

/** Builds small worlds for the sim tests. */
final class Fixtures {
	private Fixtures() {}

	static final Place CENTRE = new Place("minecraft:overworld", 0, 64, 0);

	/** A camp founded at the given time with {@code residents} people. Returns the settlement id (always 1). */
	static long camp(SimState s, long time, int residents) {
		Event founded = s.ledger().append(EventDraft.of(EventTypes.SETTLEMENT_FOUNDED, time).at(CENTRE)
				.actor(Ref.settlement(1)));
		long sid = s.nextId("settlement");
		s.put(new Settlement(sid, "Camp", "p1", CENTRE, Balance.STARTING_CLAIM_RADIUS_BLOCKS, List.of(), null, founded.id()), founded);
		for (int i = 0; i < residents; i++) addResident(s, sid, "Person" + i, Profession.NONE, time);
		return sid;
	}

	static Character addResident(SimState s, long sid, String name, Profession p, long time) {
		Settlement st = s.settlement(sid).orElseThrow();
		long id = s.nextId("character");
		Event e = s.ledger().append(EventDraft.of(EventTypes.CHARACTER_CREATED, time).actor(Ref.character(id))
				.cause(st.foundedEventId()));
		Character c = new Character(id, name, "calm", "rest", p, List.of(), Needs.MET, Condition.WELL, sid);
		s.put(c, e);
		s.put(s.settlement(sid).orElseThrow().withResident(id), e);
		return c;
	}

	static Event kill(SimState s, long characterId, long time) {
		Character c = s.character(characterId).orElseThrow();
		Event e = s.ledger().append(EventDraft.of(EventTypes.CHARACTER_DIED, time).actor(Ref.character(characterId))
				.cause(s.settlement(c.settlementId()).orElseThrow().foundedEventId()));
		s.put(c.withCondition(Condition.DEAD), e);
		return e;
	}

	static long day(long d) { return d * GameTime.TICKS_PER_DAY; }
}
