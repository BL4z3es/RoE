package io.github.bl4z3es.riseofempires.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.ActOutcome;
import io.github.bl4z3es.riseofempires.core.model.Raid;
import io.github.bl4z3es.riseofempires.core.model.RaidStage;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import io.github.bl4z3es.riseofempires.core.model.Stockpile;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RaidsTest {
	private static SimState world(int residents) {
		SimState s = new SimState(42);
		Seasons.update(s, 0);
		Fixtures.camp(s, 0, residents);
		return s;
	}

	private static void tick(SimState s, long to) {
		Seasons.update(s, to);
		Raids.update(s, to);
	}

	private static void reaches(SimState s, Event e, String type) {
		assertTrue(s.ledger().causeChain(e.id()).stream().anyMatch(x -> x.type().equals(type)), e.type() + " should reach " + type);
	}

	private static void stock(SimState s, Raid raid, Map<String, Integer> counted) {
		long sid = raid.settlementId();
		Event e = s.ledger().append(EventDraft.of(EventTypes.STOCKPILE_COUNTED, raid.arriveTime())
				.cause(s.settlement(sid).orElseThrow().foundedEventId()));
		s.put(new Stockpile(sid, counted, Map.of()), e);
	}

	@Test
	void notPlannedInSpringOrSummer() {
		SimState s = world(3);
		tick(s, Fixtures.day(1));
		tick(s, Fixtures.day(10));
		assertTrue(s.raids().isEmpty());
		tick(s, Fixtures.day(11));
		assertTrue(s.raids().isEmpty());
		tick(s, Fixtures.day(12));
		assertEquals(1, s.raids().size(), "autumn begins on day 12");
	}

	@Test
	void notPlannedBeforeEarliestDayAfterFounding() {
		SimState s = new SimState(42);
		Seasons.update(s, 0);
		Seasons.update(s, Fixtures.day(12));
		Fixtures.camp(s, Fixtures.day(12), 3);
		tick(s, Fixtures.day(13));
		assertTrue(s.raids().isEmpty());
		tick(s, Fixtures.day(12 + Balance.RAID_EARLIEST_DAY_AFTER_FOUNDING));
		assertEquals(1, s.raids().size());
	}

	@Test
	void notPlannedInAutumnBeforeEarliestDay() {
		SimState s = new SimState(42);
		Seasons.update(s, 0);
		Seasons.update(s, Fixtures.day(17));
		Fixtures.camp(s, Fixtures.day(17), 3);
		tick(s, Fixtures.day(18));
		assertTrue(s.raids().isEmpty(), "one day after founding");
		tick(s, Fixtures.day(19));
		assertEquals(1, s.raids().size());
	}

	@Test
	void sizeIsClamped() {
		SimState small = world(1);
		tick(small, Fixtures.day(18));
		assertEquals(Balance.T1_RAID_SIZE_MIN, small.raids().iterator().next().size());
		SimState big = world(200);
		tick(big, Fixtures.day(18));
		assertEquals(Balance.T1_RAID_SIZE_MAX, big.raids().iterator().next().size());
		SimState mid = world(3);
		tick(mid, Fixtures.day(18));
		assertEquals(5, mid.raids().iterator().next().size());
	}

	@Test
	void sightedThenArrivedAtTheRightTimes() {
		SimState s = world(3);
		long t = Fixtures.day(18);
		tick(s, t);
		Raid raid = s.raids().iterator().next();
		assertEquals(t + Balance.RAID_SIGHT_DELAY_TICKS, raid.sightTime());
		assertEquals(raid.sightTime() + Balance.RAID_ARRIVAL_DELAY_TICKS, raid.arriveTime());
		assertEquals(RaidStage.PLANNED, raid.stage());
		tick(s, raid.sightTime() - 1);
		assertEquals(RaidStage.PLANNED, s.raid(raid.id()).orElseThrow().stage());
		tick(s, raid.sightTime());
		assertEquals(RaidStage.SIGHTED, s.raid(raid.id()).orElseThrow().stage());
		tick(s, raid.arriveTime() - 1);
		assertEquals(RaidStage.SIGHTED, s.raid(raid.id()).orElseThrow().stage());
		tick(s, raid.arriveTime());
		assertEquals(RaidStage.ARRIVED, s.raid(raid.id()).orElseThrow().stage());
		Event arrived = s.ledger().latestOfType(EventTypes.RAID_ARRIVED).orElseThrow();
		reaches(s, arrived, EventTypes.SETTLEMENT_FOUNDED);
		reaches(s, arrived, EventTypes.RAID_SIGHTED);
		assertEquals(3, s.ledger().latestOfType(EventTypes.RAID_SIGHTED).orElseThrow().witnesses().size());
	}

	@Test
	void originIsBeyondSight() {
		SimState s = world(3);
		tick(s, Fixtures.day(18));
		Raid raid = s.raids().iterator().next();
		double d = Math.sqrt(raid.origin().horizontalDistanceSq(Fixtures.CENTRE));
		assertEquals(Balance.RAID_ORIGIN_DISTANCE_BLOCKS, d, 1.5);
	}

	@Test
	void plannedOnlyOncePerSettlement() {
		SimState s = world(3);
		tick(s, Fixtures.day(18));
		tick(s, Fixtures.day(19));
		assertEquals(1, s.raids().size());
	}

	@Test
	void killingAllRaidersEndsIt() {
		SimState s = world(3);
		tick(s, Fixtures.day(18));
		Raid raid = s.raids().iterator().next();
		tick(s, raid.arriveTime());
		Ref killer = Ref.character(1);
		List<Event> last = List.of();
		for (int i = 0; i < raid.size(); i++) last = Raids.raiderKilled(s, raid.id(), killer, raid.arriveTime() + i);
		assertEquals(2, last.size());
		assertEquals(EventTypes.RAID_ENDED, last.get(1).type());
		assertEquals("driven off", last.get(1).effects().get(0).detail());
		Raid done = s.raid(raid.id()).orElseThrow();
		assertEquals(RaidStage.ENDED, done.stage());
		assertEquals(0, done.raidersLeft());
		reaches(s, last.get(1), EventTypes.SETTLEMENT_FOUNDED);
	}

	@Test
	void theftReducesStockpileAndEndsRaid() {
		SimState s = world(3);
		tick(s, Fixtures.day(18));
		Raid raid = s.raids().iterator().next();
		tick(s, raid.arriveTime());
		long sid = raid.settlementId();
		stock(s, raid, Map.of("minecraft:wheat", 10, "minecraft:stone", 5));
		List<Event> out = Raids.stolen(s, raid.id(), Map.of("minecraft:wheat", 30), raid.arriveTime() + 5);
		assertEquals(EventTypes.GOODS_STOLEN, out.get(0).type());
		assertEquals(EventTypes.RAID_ENDED, out.get(1).type());
		assertEquals("plundered", out.get(1).effects().get(0).detail());
		assertEquals(0, s.stockpile(sid).total("minecraft:wheat"));
		assertEquals(5, s.stockpile(sid).total("minecraft:stone"));
		assertEquals(out.get(0).id(), s.lastStockEvent(sid));
		reaches(s, out.get(1), EventTypes.SETTLEMENT_FOUNDED);
	}

	@Test
	void unwatchedRaidTakesHalfTheFood() {
		SimState s = world(3);
		tick(s, Fixtures.day(18));
		Raid raid = s.raids().iterator().next();
		tick(s, raid.arriveTime());
		long sid = raid.settlementId();
		stock(s, raid, Map.of("minecraft:wheat", 11, "minecraft:stone", 8));
		List<Event> out = Raids.resolveUnwatched(s, raid.id(), "minecraft:wheat"::equals, raid.arriveTime());
		assertEquals(2, out.size());
		assertEquals(6, s.stockpile(sid).total("minecraft:wheat"));
		assertEquals(8, s.stockpile(sid).total("minecraft:stone"));
		assertEquals(RaidStage.ENDED, s.raid(raid.id()).orElseThrow().stage());
		reaches(s, out.get(1), EventTypes.SETTLEMENT_FOUNDED);
	}

	@Test
	void raidChainReachesFoundingAndSeason() {
		SimState s = world(3);
		tick(s, Fixtures.day(18));
		Event planned = s.ledger().latestOfType(EventTypes.RAID_PLANNED).orElseThrow();
		reaches(s, planned, EventTypes.SETTLEMENT_FOUNDED);
		reaches(s, planned, EventTypes.SEASON_CHANGED);
		assertEquals(ActOutcome.UNDECIDED, s.actOne(1));
	}
}
