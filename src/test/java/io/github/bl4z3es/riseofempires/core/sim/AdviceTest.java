package io.github.bl4z3es.riseofempires.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.model.Blueprint;
import io.github.bl4z3es.riseofempires.core.model.BuildSite;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Condition;
import io.github.bl4z3es.riseofempires.core.model.Profession;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdviceTest {
	private static String key(SimState s, long now, int food) {
		return Advice.next(s, 1, now, food).map(Advice.Line::key).orElse("none");
	}

	private static Event ev(SimState s, long sid, String type) {
		return s.ledger().append(EventDraft.of(type, 0).cause(s.settlement(sid).orElseThrow().foundedEventId()));
	}

	private static void site(SimState s, String blueprint, int placed, int total) {
		Event e = ev(s, 1, EventTypes.BLUEPRINT_PLACED);
		s.put(new BuildSite(s.nextId("site"), 1, blueprint, Fixtures.CENTRE, 0, placed, total, e.id()), e);
	}

	@Test
	void rulesInPriorityOrder() {
		SimState s = new SimState(1);
		Seasons.update(s, 0);
		assertEquals("none", key(s, 0, 0));
		long sid = Fixtures.camp(s, 0, 3);
		Character a = s.residents(sid).get(0);

		assertEquals("advice.granary", key(s, 0, 0));
		Event g = ev(s, sid, EventTypes.GRANARY_SEALED);
		s.put(s.settlement(sid).orElseThrow().withGranary(Fixtures.CENTRE), g);

		assertEquals("advice.farm", key(s, 0, 0));
		site(s, Blueprint.FARM, 10, 10);

		assertEquals("advice.farmer", key(s, 0, 0));
		s.put(a.withProfession(Profession.FARMER), ev(s, sid, EventTypes.JOB_ASSIGNED));

		site(s, Blueprint.STABLE, 1, 10);
		assertEquals("advice.builder", key(s, 0, 0));
		Character b = s.residents(sid).get(1);
		s.put(b.withProfession(Profession.BUILDER), ev(s, sid, EventTypes.JOB_ASSIGNED));

		assertEquals("advice.bunkhouse", key(s, 0, 0));
		site(s, Blueprint.BUNKHOUSE, 10, 10);

		Advice.Line food = Advice.next(s, sid, 0, 8).orElseThrow();
		assertEquals("advice.food", food.key());
		assertEquals(List.of("2", Integer.toString(ActOne.winterNeedQuarters(3) / 4)), food.args());
		assertEquals("none", key(s, Fixtures.day(18), 8));
		assertEquals("none", key(s, 0, ActOne.winterNeedQuarters(3)));

		Character c = s.residents(sid).get(2);
		s.put(c.withCondition(Condition.WEAK), ev(s, sid, EventTypes.CONDITION_CHANGED));
		Advice.Line unwell = Advice.next(s, sid, 0, ActOne.winterNeedQuarters(3)).orElseThrow();
		assertEquals("advice.unwell", unwell.key());
		assertEquals(List.of(c.name(), "WEAK"), unwell.args());
	}

	@Test
	void bedsFactSilencesBunkhouseAdvice() {
		SimState s = new SimState(1);
		Seasons.update(s, 0);
		long sid = Fixtures.camp(s, 0, 1);
		Event g = ev(s, sid, EventTypes.GRANARY_SEALED);
		s.put(s.settlement(sid).orElseThrow().withGranary(Fixtures.CENTRE), g);
		site(s, Blueprint.FARM, 10, 10);
		s.put(s.residents(sid).get(0).withProfession(Profession.FARMER), ev(s, sid, EventTypes.JOB_ASSIGNED));
		assertEquals("advice.bunkhouse", key(s, 0, 100));
		s.putFact("beds." + sid, "1", ev(s, sid, EventTypes.BLUEPRINT_COMPLETED));
		assertEquals("none", key(s, 0, 100));
	}

	@Test
	void raidOutranksEverything() {
		SimState s = new SimState(1);
		Seasons.update(s, 0);
		long sid = Fixtures.camp(s, 0, 3);
		Seasons.update(s, Fixtures.day(18));
		Raids.update(s, Fixtures.day(18));
		var raid = s.raids().iterator().next();
		Raids.update(s, raid.sightTime());
		Advice.Line line = Advice.next(s, sid, raid.sightTime(), 0).orElseThrow();
		assertEquals("advice.raid", line.key());
		assertEquals(Integer.toString(raid.size()), line.args().get(0));
		assertEquals("24", line.args().get(1));
		assertEquals(EventTypes.RAID_SIGHTED, s.ledger().get(line.causeEventId()).orElseThrow().type());
		Raids.update(s, raid.arriveTime());
		assertEquals("0", Advice.next(s, sid, raid.arriveTime(), 0).orElseThrow().args().get(1));
	}

	@Test
	void noAdviceWhenEveryoneIsGone() {
		SimState s = new SimState(1);
		Seasons.update(s, 0);
		long sid = Fixtures.camp(s, 0, 1);
		Fixtures.kill(s, s.settlement(sid).orElseThrow().population().get(0), 5);
		assertTrue(Advice.next(s, sid, 10, 0).isEmpty());
	}
}
