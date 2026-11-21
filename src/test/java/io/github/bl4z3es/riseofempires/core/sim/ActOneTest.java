package io.github.bl4z3es.riseofempires.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.model.ActOutcome;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import io.github.bl4z3es.riseofempires.core.model.Stockpile;
import java.util.List;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

class ActOneTest {
	private static final ToIntFunction<Stockpile> FOOD = st -> st.total("minecraft:wheat") * 4;

	private static void tick(SimState s, long to) {
		Seasons.update(s, to);
		ActOne.update(s, to, FOOD);
	}

	@Test
	void winterIndexFromFounding() {
		assertEquals(3, ActOne.winterIndex(0));
		assertEquals(3, ActOne.winterIndex(Fixtures.day(10)));
		assertEquals(7, ActOne.winterIndex(Fixtures.day(19)));
	}

	@Test
	void warnsOneSeasonAhead() {
		SimState s = new SimState(1);
		Seasons.update(s, 0);
		long sid = Fixtures.camp(s, 0, 3);
		tick(s, Fixtures.day(11));
		assertTrue(s.fact("winter_warned." + sid).isEmpty());
		tick(s, Fixtures.day(12));
		Event w = s.ledger().latestOfType(EventTypes.WINTER_WARNING).orElseThrow();
		assertEquals(Fixtures.day(12), w.time());
		assertEquals(Integer.toString(3 * 2 * 6 * 5), w.effects().get(0).detail());
		assertTrue(w.causes().contains(Long.parseLong(s.fact("season.event.2").orElseThrow())));
		tick(s, Fixtures.day(13));
		assertEquals(1, s.ledger().all().stream().filter(e -> e.type().equals(EventTypes.WINTER_WARNING)).count());
	}

	@Test
	void survivesWithOneResidentAlive() {
		SimState s = new SimState(1);
		Seasons.update(s, 0);
		long sid = Fixtures.camp(s, 0, 3);
		List<Long> ids = s.settlement(sid).orElseThrow().population();
		tick(s, Fixtures.day(13));
		Fixtures.kill(s, ids.get(0), Fixtures.day(14));
		Fixtures.kill(s, ids.get(1), Fixtures.day(20));
		tick(s, Fixtures.day(23));
		assertEquals(ActOutcome.UNDECIDED, s.actOne(sid));
		tick(s, Fixtures.day(24));
		assertEquals(ActOutcome.SURVIVED, s.actOne(sid));
		Event end = s.ledger().latestOfType(EventTypes.ACT_ONE_ENDED).orElseThrow();
		assertEquals("SURVIVED", end.effects().get(0).detail());
		assertTrue(s.ledger().causeChain(end.id()).stream().anyMatch(e -> e.type().equals(EventTypes.WINTER_WARNING)));
		assertTrue(s.ledger().causeChain(end.id()).stream().anyMatch(e -> e.type().equals(EventTypes.SETTLEMENT_FOUNDED)));
	}

	@Test
	void lostWhenEveryoneIsGone() {
		SimState s = new SimState(1);
		Seasons.update(s, 0);
		long sid = Fixtures.camp(s, 0, 2);
		List<Long> ids = s.settlement(sid).orElseThrow().population();
		tick(s, Fixtures.day(13));
		Event d1 = Fixtures.kill(s, ids.get(0), Fixtures.day(14));
		Event d2 = Fixtures.kill(s, ids.get(1), Fixtures.day(19));
		tick(s, Fixtures.day(19));
		assertEquals(ActOutcome.LOST, s.actOne(sid));
		Event end = s.ledger().latestOfType(EventTypes.ACT_ONE_ENDED).orElseThrow();
		assertTrue(end.causes().containsAll(List.of(d1.id(), d2.id())));
		tick(s, Fixtures.day(30));
		assertEquals(ActOutcome.LOST, s.actOne(sid));
	}

	@Test
	void explainReturnsTheDeaths() {
		SimState s = new SimState(1);
		Seasons.update(s, 0);
		long sid = Fixtures.camp(s, 0, 2);
		assertTrue(ActOne.explain(s, sid).isEmpty());
		List<Long> ids = s.settlement(sid).orElseThrow().population();
		Event d1 = Fixtures.kill(s, ids.get(0), Fixtures.day(14));
		Event d2 = Fixtures.kill(s, ids.get(1), Fixtures.day(19));
		tick(s, Fixtures.day(19));
		List<Event> why = ActOne.explain(s, sid);
		assertEquals(EventTypes.ACT_ONE_ENDED, why.get(0).type());
		List<Long> rest = why.subList(1, why.size()).stream().map(Event::id).toList();
		assertTrue(rest.contains(d1.id()) && rest.contains(d2.id()));
		assertEquals(rest.stream().sorted().toList(), rest);
	}
}
