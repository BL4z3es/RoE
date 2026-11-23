package io.github.bl4z3es.riseofempires.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import java.util.List;
import org.junit.jupiter.api.Test;

class SeasonsTest {
	@Test
	void dayToSeasonMapping() {
		long[] days = {0, 5, 6, 17, 18, 23, 24};
		Season[] expected = {Season.SPRING, Season.SPRING, Season.SUMMER, Season.AUTUMN, Season.WINTER, Season.WINTER, Season.SPRING};
		for (int i = 0; i < days.length; i++) assertEquals(expected[i], Calendar.season(Fixtures.day(days[i])), "day " + days[i]);
		assertEquals(2, Calendar.year(Fixtures.day(24)));
	}

	@Test
	void firstCallBeginsWorldAndAnnouncesNothing() {
		SimState s = new SimState(1);
		List<Event> out = Seasons.update(s, Fixtures.day(7));
		assertEquals(1, out.size());
		assertEquals(EventTypes.WORLD_BEGAN, out.get(0).type());
		assertTrue(out.get(0).isRoot());
		assertTrue(Seasons.update(s, Fixtures.day(7)).isEmpty());
	}

	@Test
	void seasonChangesChainBackToWorldBegan() {
		SimState s = new SimState(1);
		Seasons.update(s, 0);
		List<Event> out = Seasons.update(s, Fixtures.day(6));
		assertEquals(1, out.size());
		Event e = out.get(0);
		assertEquals(EventTypes.SEASON_CHANGED, e.type());
		assertEquals(Fixtures.day(6), e.time());
		assertEquals("Summer of year 1", e.effects().get(0).detail());
		assertEquals(EventTypes.WORLD_BEGAN, s.ledger().roots(e.id()).get(0).type());
		assertEquals(Long.toString(e.id()), s.fact("season.event.1").orElseThrow());
	}

	@Test
	void catchUpWritesOneEventPerBoundary() {
		SimState s = new SimState(1);
		Seasons.update(s, 0);
		List<Event> out = Seasons.update(s, Fixtures.day(25));
		assertEquals(4, out.size());
		assertEquals("Spring of year 2", out.get(3).effects().get(0).detail());
		for (int i = 1; i < out.size(); i++) assertTrue(out.get(i).causes().contains(out.get(i - 1).id()));
		assertEquals(EventTypes.WORLD_BEGAN, s.ledger().roots(out.get(3).id()).get(0).type());
	}
}
