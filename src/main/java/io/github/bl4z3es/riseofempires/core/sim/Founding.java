package io.github.bl4z3es.riseofempires.core.sim;

import java.util.List;
import java.util.Optional;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.ActOutcome;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import io.github.bl4z3es.riseofempires.core.model.Stockpile;

/** Taking the Charter and founding a camp (CONTEXT.md section 2, Opening). */
public final class Founding {
	private Founding() {}

	/** The player takes the Charter from the Hollow Ruin. */
	public static Event charterTaken(SimState s, String playerUuid, Place where, long now) {
		EventDraft d = EventDraft.of(EventTypes.CHARTER_TAKEN, now).at(where).actor(Ref.player(playerUuid));
		s.fact("ruin.event").ifPresent(v -> d.cause(Long.parseLong(v)));
		Event e = s.ledger().append(d);
		s.putFact("charter." + playerUuid, Long.toString(e.id()), e);
		return e;
	}

	/** Founds a camp. Works once per player. */
	public static Optional<Event> found(SimState s, String playerUuid, String name, Place centre, long now) {
		if (s.settlementFoundedBy(playerUuid).isPresent()) return Optional.empty();
		long id = s.nextId("settlement");
		EventDraft d = EventDraft.of(EventTypes.SETTLEMENT_FOUNDED, now).at(centre)
				.actor(Ref.player(playerUuid)).actor(Ref.settlement(id))
				.effect(Ref.settlement(id), "claim radius", Integer.toString(Balance.STARTING_CLAIM_RADIUS_BLOCKS))
				.effect(Ref.settlement(id), "centre", centre.toString());
		s.fact("charter." + playerUuid).ifPresent(v -> d.cause(Long.parseLong(v)));
		Event e = s.ledger().append(d);
		s.put(new Settlement(id, name, playerUuid, centre, Balance.STARTING_CLAIM_RADIUS_BLOCKS, List.of(), null, e.id()), e);
		s.recordFounder(playerUuid, id, e);
		s.putActOne(id, ActOutcome.UNDECIDED, e);
		s.put(Stockpile.empty(id), e);
		s.noteStockEvent(id, e);
		return Optional.of(e);
	}

	/** The settlement whose claim contains the place; the lowest id wins. */
	public static Optional<Settlement> settlementAt(SimState s, Place p) {
		for (Settlement st : s.settlements()) {
			if (st.claims(p)) return Optional.of(st);
		}
		return Optional.empty();
	}
}
