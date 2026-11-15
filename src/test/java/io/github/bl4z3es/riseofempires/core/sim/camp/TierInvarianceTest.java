package io.github.bl4z3es.riseofempires.core.sim.camp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.bl4z3es.riseofempires.core.ledger.Effect;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Professions;
import io.github.bl4z3es.riseofempires.core.sim.Effects;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Task M1-18 and rule E3. The same camp runs for ten in-game days in the Ledger tier and in the
 * embodied tier. In the embodied run a small driver plays the Minecraft layer's part: it asks
 * for each person's next order, begins it, and completes it one hour later than the Ledger tier
 * would, as a body walking to the field and back would.
 */
class TierInvarianceTest {
    /** Food produced and eaten may differ by at most this share between the tiers. */
    static final double TOLERANCE = 0.10;
    static final int DAYS = 10;
    /** Extra hours a body takes over the Ledger tier's figure, for walking. */
    static final int WALK_LAG_HOURS = 1;

    @Test
    void foodProducedAndEatenAgreeBetweenTiers() {
        double[] ledger = run(false);
        double[] embodied = run(true);
        assertTrue(ledger[0] > 0, "the farmer produced food");
        assertTrue(ledger[1] > 0, "people ate");
        assertEquals(ledger[0], embodied[0], ledger[0] * TOLERANCE, "food produced");
        assertEquals(ledger[1], embodied[1], ledger[1] * TOLERANCE, "food eaten");
    }

    /** Returns {meals produced, meals eaten}. */
    private static double[] run(boolean embodied) {
        Camp camp = new Camp(1234);
        camp.beds(3);
        // Enough bread that nobody weakens: the test measures the tiers, not a famine.
        camp.addStock(Food.BREAD, 80);
        camp.assign(0, Professions.FARMER);
        camp.assign(1, Professions.GATHERER);
        camp.assign(2, Professions.BUILDER);
        BuildSites.open(camp.sim, camp.id, "bunkhouse-1", "bunkhouse",
                List.of(new ItemCount(Food.OAK_LOG, 12), new ItemCount(Food.COBBLESTONE, 24),
                        new ItemCount(Food.OAK_LOG, 12)), 0);
        camp.sim.state().setEmbodied(camp.id, embodied);
        Jobs jobs = camp.jobs();
        long end = camp.sim.state().hour() + DAYS * 24L;
        while (camp.sim.state().hour() < end) {
            camp.sim.advanceHour();
            if (!embodied) continue;
            for (Character c : camp.people()) {
                WorkOrder current = Jobs.currentOrder(camp.sim.state(), c.id());
                if (current != null) {
                    double due = camp.sim.state().stat(new io.github.bl4z3es.riseofempires.core.ledger.Ref("character", c.id()), Jobs.DUE_HOUR);
                    if (camp.sim.state().hour() >= due + WALK_LAG_HOURS) jobs.complete(camp.sim, c.id(), current);
                } else {
                    WorkOrder next = jobs.nextOrder(camp.sim, c.id());
                    if (!next.isIdle()) jobs.begin(camp.sim, c.id(), next);
                }
            }
        }
        double produced = 0, eaten = 0;
        for (Event e : camp.sim.ledger().all()) {
            for (Effect f : e.effects()) {
                if (!f.kind().equals(Effects.STOCK_ADD) || !Food.isFood(f.key())) continue;
                double meals = f.amount() * Food.MEAL_VALUES.get(f.key());
                if (e.type().equals(Jobs.DONE) && meals > 0) produced += meals;
                if (e.type().equals(Needs.MEAL_SERVED) && meals < 0) eaten -= meals;
            }
        }
        return new double[] {produced, eaten};
    }
}
