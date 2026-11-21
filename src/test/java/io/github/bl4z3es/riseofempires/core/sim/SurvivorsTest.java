package io.github.bl4z3es.riseofempires.core.sim;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.SimState;

class SurvivorsTest {
	private static final Place CENTRE = new Place("overworld", 0, 64, 0);
	private static final List<Survivors.Template> POOL = List.of(
			new Survivors.Template("Ada", "stubborn", "a roof", "the fire"),
			new Survivors.Template("Bram", "kind", "a field", "the flood"),
			new Survivors.Template("Cole", "quiet", "a forge", "the road"),
			new Survivors.Template("Dena", "bold", "a name", "the wall"),
			new Survivors.Template("Eli", "wary", "peace", "the cold"));

	private static SimState founded(long seed) {
		SimState s = new SimState(seed);
		Founding.found(s, "p1", "Camp", CENTRE, 1000).orElseThrow();
		return s;
	}

	@Test
	void setOutTimeIsDuskAfterFounding() {
		assertEquals(12500, Survivors.setOutTime(1000));
		assertEquals(24000 + 12500, Survivors.setOutTime(13000));
		assertEquals(24000 + 12500, Survivors.setOutTime(12500));
		assertEquals(48000 + 12500, Survivors.setOutTime(24000 + 20000));
	}

	@Test
	void threeDistinctSurvivorsWithDetails() {
		SimState s = founded(7);
		Survivors.setOut(s, 1, 12500, POOL);
		List<Long> ids = Survivors.pending(s, 1);
		assertEquals(3, ids.size());
		Set<String> names = new HashSet<>();
		for (long id : ids) {
			Character c = s.character(id).orElseThrow();
			names.add(c.name());
			assertFalse(c.trait().isBlank());
			assertFalse(c.want().isBlank());
			assertEquals(1, c.memories().size());
			assertFalse(c.memories().get(0).text().isBlank());
			assertEquals(0, c.settlementId());
		}
		assertEquals(3, names.size());
	}

	@Test
	void deterministicForSeed() {
		SimState a = founded(42), b = founded(42);
		Survivors.setOut(a, 1, 12500, POOL);
		Survivors.setOut(b, 1, 12500, POOL);
		assertEquals(names(a), names(b));
	}

	private static List<String> names(SimState s) {
		return Survivors.pending(s, 1).stream().map(id -> s.character(id).orElseThrow().name()).toList();
	}

	@Test
	void smallPoolThrows() {
		SimState s = founded(1);
		assertThrows(IllegalArgumentException.class, () -> Survivors.setOut(s, 1, 0, POOL.subList(0, 2)));
	}

	@Test
	void joinAddsToPopulationWithCauseChain() {
		SimState s = founded(7);
		Event out = Survivors.setOut(s, 1, 12500, POOL);
		long id = Survivors.pending(s, 1).get(0);
		Event joined = Survivors.join(s, id, 1, 13000, CENTRE);
		assertEquals(EventTypes.SURVIVOR_JOINED, joined.type());
		assertEquals(List.of(out.id()), joined.causes());
		assertEquals(1, s.character(id).orElseThrow().settlementId());
		assertTrue(s.settlement(1).orElseThrow().population().contains(id));
		assertEquals(1, s.residents(1).size());
		assertEquals(2, Survivors.pending(s, 1).size());
		assertTrue(s.ledger().causeChain(joined.id()).stream()
				.anyMatch(e -> e.type().equals(EventTypes.SETTLEMENT_FOUNDED)));
	}
}
