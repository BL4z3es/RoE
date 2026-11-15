package io.github.bl4z3es.riseofempires.core.sim.camp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.model.Professions;
import io.github.bl4z3es.riseofempires.core.sim.CampRules;
import java.util.List;
import org.junit.jupiter.api.Test;

class RaidsTest {
    private static Camp fedCamp() {
        Camp camp = new Camp(9);
        camp.beds(3);
        camp.addStock(Food.BREAD, 400);
        return camp;
    }

    @Test
    void standingComesFromPopulationAndTerritory() {
        Camp camp = fedCamp();
        Standing s = Standing.of(camp.sim.state(), camp.settlement().nationId());
        assertEquals(3, s.population());
        assertEquals(Balance.STANDING_POPULATION_WEIGHT * Math.log(3) / Math.log(2), s.populationPart(), 1e-9);
        double chunks = Math.PI * Balance.STARTING_CLAIM_RADIUS_BLOCKS * Balance.STARTING_CLAIM_RADIUS_BLOCKS / 256;
        assertEquals(chunks, s.territoryChunks(), 1e-9);
        assertEquals(s.populationPart() + s.territoryPart(), s.score(), 1e-9);
    }

    @Test
    void theRaidIsAnnouncedWellBeforeItArrivesAndIsDrivenOffByAHealthyCamp() {
        Camp camp = fedCamp();
        camp.runToDay(Balance.RAID_FIRST_DAY + 3);
        Event planned = camp.events(Raids.PLANNED).get(0);
        Event announced = camp.events(Raids.ANNOUNCED).get(0);
        Event arrived = camp.events(Raids.ARRIVED).get(0);
        Event resolved = camp.events(Raids.RESOLVED).get(0);
        assertEquals(List.of(planned.id()), announced.causes());
        assertEquals(List.of(announced.id()), arrived.causes());
        assertEquals(List.of(arrived.id()), resolved.causes());
        assertTrue(arrived.hour() - announced.hour() >= Balance.RAID_WARNING_HOURS);
        int size = (int) camp.sim.state().stat(Raids.ref(Raids.raidOn(camp.sim.state(), camp.id)), Raids.SIZE);
        assertTrue(size >= Balance.T1_RAID_MIN && size <= Balance.T1_RAID_MAX);
        assertTrue(resolved.summary().contains("driven off"));
        assertEquals(3, camp.people().size());
        assertEquals(1, camp.events(Raids.PLANNED).size(), "one raid in Act I");
    }

    @Test
    void theCalculatorLetsRaidersTakeFoodAndLivesFromAWeakDefence() {
        Camp camp = fedCamp();
        camp.sim.state().setEmbodied(camp.id, true);
        String raid = "raid-" + camp.id;
        while (!Raids.state(camp.sim.state(), raid).equals(Raids.STATE_ARRIVED)) camp.sim.advanceHour();
        People.die(camp.sim, camp.people().get(0).id(), "test death", List.of());
        People.die(camp.sim, camp.people().get(0).id(), "test death", List.of());
        RaidOutcome outcome = CampRules.raids(camp.sim).calculate(camp.sim, raid);
        assertFalse(outcome.repelled());
        int size = (int) camp.sim.state().stat(Raids.ref(raid), Raids.SIZE);
        assertEquals(List.of(new ItemCount(Food.BREAD, size * Balance.RAID_FOOD_PER_RAIDER)), outcome.foodStolen());
        assertEquals(List.of(camp.people().get(0).id()), outcome.killed());
    }

    @Test
    void hungerAfterARaidTracesBackToTheRaid() {
        Camp camp = fedCamp();
        camp.sim.state().setEmbodied(camp.id, true);
        String raid = "raid-" + camp.id;
        while (!Raids.state(camp.sim.state(), raid).equals(Raids.STATE_ARRIVED)) camp.sim.advanceHour();
        Event resolved = CampRules.raids(camp.sim).resolve(camp.sim, raid,
                new RaidOutcome(false, 0, List.of(new ItemCount(Food.BREAD, camp.stock(Food.BREAD))), List.of()));
        camp.runDays(1);
        Event out = camp.events(Needs.FOOD_RAN_OUT).get(0);
        assertTrue(out.causes().contains(resolved.id()), "the granary ran out because of the raid");
    }

    @Test
    void aWatchedFightIsReportedByTheWorldAndTheCalculatorStaysOut() {
        Camp camp = fedCamp();
        camp.assign(0, Professions.FIGHTER);
        camp.sim.state().setEmbodied(camp.id, true);
        String raid = "raid-" + camp.id;
        while (!Raids.state(camp.sim.state(), raid).equals(Raids.STATE_ARRIVED)) camp.sim.advanceHour();
        camp.sim.advanceHour();
        assertTrue(camp.events(Raids.RESOLVED).isEmpty());
        String victim = camp.people().get(2).id();
        CampRules.raids(camp.sim).resolve(camp.sim, raid, new RaidOutcome(false, 1,
                List.of(new ItemCount(Food.BREAD, 5)), List.of(victim)));
        Event death = camp.events(People.DEATH).get(0);
        assertTrue(camp.chainHas(death, Raids.ARRIVED));
        assertEquals(2, camp.people().size());
        assertFalse(camp.events(Raids.RESOLVED).isEmpty());
    }

    @Test
    void aWatchedFightNobodyReportsIsSettledAfterTheLimit() {
        Camp camp = fedCamp();
        camp.sim.state().setEmbodied(camp.id, true);
        camp.runToDay(Balance.RAID_FIRST_DAY + 3);
        Event arrived = camp.events(Raids.ARRIVED).get(0);
        Event resolved = camp.events(Raids.RESOLVED).get(0);
        assertEquals(Balance.RAID_MAX_BATTLE_HOURS, resolved.hour() - arrived.hour());
    }
}
