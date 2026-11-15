package io.github.bl4z3es.riseofempires.core.sim.camp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Professions;
import io.github.bl4z3es.riseofempires.core.sim.Calendar;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobsTest {
    @Test
    void noOutdoorGrowthInWinterAndFasterInSpring() {
        assertEquals(0.0, Farming.growthMultiplier(Calendar.Season.WINTER, true));
        assertEquals(1.0, Farming.growthMultiplier(Calendar.Season.WINTER, false));
        assertEquals(Balance.SPRING_GROWTH_MULTIPLIER, Farming.growthMultiplier(Calendar.Season.SPRING, true));
        assertEquals(1.0, Farming.growthMultiplier(Calendar.Season.SUMMER, true));
        long winter = Calendar.hourOfDayStart(Balance.SEASON_LENGTH_DAYS * 3);
        assertEquals(0.0, Farming.growth(winter, winter + Balance.SEASON_LENGTH_DAYS * 24, true));
        assertFalse(Farming.ripe(winter, winter + Balance.SEASON_LENGTH_DAYS * 24, true));
        long hoursInSpring = (long) Math.ceil(Balance.CROP_GROWTH_HOURS / Balance.SPRING_GROWTH_MULTIPLIER);
        assertTrue(Farming.ripe(0, hoursInSpring, true));
        assertFalse(Farming.ripe(Calendar.hourOfDayStart(6), Calendar.hourOfDayStart(6) + hoursInSpring, true));
    }

    @Test
    void aFarmerPlantsHarvestsAndDepositsWithNoHelp() {
        Camp camp = new Camp(5);
        camp.beds(3);
        camp.addStock(Food.BREAD, 200);
        camp.assign(0, Professions.FARMER);
        String farmer = camp.people().get(0).id();
        int seeds = camp.stock(Food.SEEDS);
        camp.runDays(3);

        List<Event> done = camp.events(Jobs.DONE);
        assertTrue(done.size() >= 2, "planted and harvested");
        Event harvest = done.stream().filter(e -> e.summary().contains("harvested")).findFirst().orElseThrow();
        assertTrue(harvest.actors().contains(Ref.character(farmer)));
        assertTrue(camp.chainHas(harvest, Jobs.BEGUN));
        assertTrue(camp.chainHas(harvest, Jobs.ASSIGNED));
        int planted = Math.min(seeds, Balance.FARM_PLOT_CROPS);
        assertEquals((int) (planted * Balance.HARVEST_WHEAT_PER_CROP), camp.stock(Food.WHEAT));
    }

    @Test
    void aGathererKeepsWoodAndStoneEven() {
        Camp camp = new Camp(5);
        camp.beds(3);
        camp.addStock(Food.BREAD, 200);
        camp.assign(1, Professions.GATHERER);
        camp.runDays(2);
        assertTrue(camp.stock(Food.OAK_LOG) > 0);
        assertTrue(camp.stock(Food.COBBLESTONE) > 0);
    }

    @Test
    void buildersFinishASiteFromTheStockpileAndWaitWhenItIsShort() {
        Camp camp = new Camp(5);
        camp.beds(3);
        camp.addStock(Food.BREAD, 200);
        camp.assign(2, Professions.BUILDER);
        String builder = camp.people().get(2).id();
        BuildSites.open(camp.sim, camp.id, "farm-1", "farm",
                List.of(new ItemCount(Food.OAK_LOG, 10), new ItemCount(Food.COBBLESTONE, 20)), 0);
        camp.runDays(0.5);
        assertTrue(camp.sim.state().tag(Ref.character(builder), Jobs.IDLE_REASON).contains("oak log"));
        camp.addStock(Food.OAK_LOG, 10);
        camp.addStock(Food.COBBLESTONE, 20);
        camp.runDays(1);
        Event finished = camp.events(BuildSites.FINISHED).get(0);
        assertTrue(camp.chainHas(finished, BuildSites.OPENED));
        assertEquals(0, camp.stock(Food.OAK_LOG));
        assertTrue(BuildSites.open(camp.sim.state(), camp.id).isEmpty());
    }

    @Test
    void embodiedTierLeavesWorkToTheWorldAndRefusesBadCalls() {
        Camp camp = new Camp(5);
        camp.beds(3);
        camp.addStock(Food.BREAD, 200);
        camp.assign(0, Professions.FARMER);
        String farmer = camp.people().get(0).id();
        camp.sim.state().setEmbodied(camp.id, true);
        camp.runDays(1);
        assertTrue(camp.events(Jobs.BEGUN).isEmpty(), "nobody works by the Ledger while watched");

        Jobs jobs = camp.jobs();
        WorkOrder order = jobs.nextOrder(camp.sim, farmer);
        assertEquals(Jobs.STEP_PLANT, order.step());
        Event begun = jobs.begin(camp.sim, farmer, order);
        assertNull(jobs.begin(camp.sim, farmer, order), "already busy");
        assertEquals(order, Jobs.currentOrder(camp.sim.state(), farmer));
        assertNull(jobs.complete(camp.sim, camp.people().get(1).id(), order), "not their order");
        Event done = jobs.complete(camp.sim, farmer, order);
        assertEquals(List.of(begun.id()), done.causes());
        assertNull(Jobs.currentOrder(camp.sim.state(), farmer));
    }
}
