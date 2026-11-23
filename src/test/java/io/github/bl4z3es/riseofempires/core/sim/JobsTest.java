package io.github.bl4z3es.riseofempires.core.sim;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.model.Profession;
import io.github.bl4z3es.riseofempires.core.model.SimState;

class JobsTest {
	private static final Place CENTRE = new Place("overworld", 0, 64, 0);

	private static SimState withSurvivor() {
		SimState s = new SimState(3);
		Founding.found(s, "p1", "Camp", CENTRE, 0).orElseThrow();
		Survivors.setOut(s, 1, 12500, List.of(
				new Survivors.Template("A", "t", "w", "m"),
				new Survivors.Template("B", "t", "w", "m"),
				new Survivors.Template("C", "t", "w", "m")));
		return s;
	}

	@Test
	void assignChangesProfessionAndNamesJoinAsCause() {
		SimState s = withSurvivor();
		long id = Survivors.pending(s, 1).get(0);
		Event joined = Survivors.join(s, id, 1, 13000, CENTRE);
		Event e = Jobs.assign(s, id, Profession.FARMER, "p1", 13100);
		assertEquals(EventTypes.JOB_ASSIGNED, e.type());
		assertEquals(List.of(joined.id()), e.causes());
		assertEquals(Profession.FARMER, s.character(id).orElseThrow().profession());
		assertEquals("NONE->FARMER", e.effects().get(0).detail());
	}

	@Test
	void assignWithoutJoinHasNoCause() {
		SimState s = withSurvivor();
		long id = Survivors.pending(s, 1).get(0);
		Event e = Jobs.assign(s, id, Profession.BUILDER, "p1", 100);
		assertTrue(e.causes().isEmpty());
		assertEquals(Profession.BUILDER, s.character(id).orElseThrow().profession());
	}
}
