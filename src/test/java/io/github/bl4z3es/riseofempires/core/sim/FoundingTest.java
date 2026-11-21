package io.github.bl4z3es.riseofempires.core.sim;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.model.SimState;

class FoundingTest {
	private static final Place CENTRE = new Place("overworld", 100, 64, 100);

	@Test
	void foundsOncePerPlayer() {
		SimState s = new SimState(1);
		assertTrue(Founding.found(s, "p1", "Camp", CENTRE, 0).isPresent());
		assertTrue(Founding.found(s, "p1", "Other", CENTRE, 10).isEmpty());
		assertEquals(1, s.settlements().size());
		assertTrue(Founding.found(s, "p2", "Second", new Place("overworld", 1000, 64, 1000), 10).isPresent());
	}

	@Test
	void claimRadiusAndSettlementAt() {
		SimState s = new SimState(1);
		Event e = Founding.found(s, "p1", "Camp", CENTRE, 0).orElseThrow();
		Settlement st = s.settlement(1).orElseThrow();
		assertEquals(32, st.claimRadius());
		assertEquals(e.id(), st.foundedEventId());
		assertEquals(1L, s.settlementFoundedBy("p1").orElseThrow());
		assertTrue(Founding.settlementAt(s, new Place("overworld", 120, 70, 110)).isPresent());
		assertTrue(Founding.settlementAt(s, new Place("overworld", 132, 64, 100)).isPresent());
		assertTrue(Founding.settlementAt(s, new Place("overworld", 140, 64, 100)).isEmpty());
		assertTrue(Founding.settlementAt(s, new Place("the_nether", 100, 64, 100)).isEmpty());
	}

	@Test
	void foundingNamesTheCharterAsCause() {
		SimState s = new SimState(1);
		Event ruin = s.ledger().append(io.github.bl4z3es.riseofempires.core.ledger.EventDraft.of(EventTypes.RUIN_PLACED, 0));
		s.putFact("ruin.event", Long.toString(ruin.id()), ruin);
		Event charter = Founding.charterTaken(s, "p1", CENTRE, 5);
		assertEquals(EventTypes.CHARTER_TAKEN, charter.type());
		assertEquals(java.util.List.of(ruin.id()), charter.causes());
		Event founded = Founding.found(s, "p1", "Camp", CENTRE, 10).orElseThrow();
		assertEquals(EventTypes.SETTLEMENT_FOUNDED, founded.type());
		assertTrue(founded.causes().contains(charter.id()));
	}
}
