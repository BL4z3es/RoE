package io.github.bl4z3es.riseofempires.core.sim.camp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.Ledger;
import io.github.bl4z3es.riseofempires.core.model.Professions;
import io.github.bl4z3es.riseofempires.core.sim.CampRules;
import io.github.bl4z3es.riseofempires.core.sim.Simulation;
import io.github.bl4z3es.riseofempires.core.sim.WorldState;
import org.junit.jupiter.api.Test;

/** Every piece of state the camp rules need lives in the world state or the Ledger. */
class SaveLoadTest {
    @Test
    void aCampReloadedMidRunCarriesOnExactlyAsIfNothingHappened() {
        Camp camp = new Camp(77);
        camp.beds(2);
        camp.assign(0, Professions.FARMER);
        camp.assign(1, Professions.GATHERER);
        camp.assign(2, Professions.BUILDER);
        BuildSites.open(camp.sim, camp.id, "farm-1", "farm", java.util.List.of(new ItemCount(Food.OAK_LOG, 18)), 0);
        camp.sim.advanceTo(5 * 24 + 7);

        Ledger copy = new Ledger();
        for (Event e : camp.sim.ledger().all()) copy.restore(Event.decode(e.encode()));
        Simulation reloaded = CampRules.install(new Simulation(WorldState.load(camp.sim.state().save()), copy));

        long end = 20 * 24 + 3;
        camp.sim.advanceTo(end);
        reloaded.advanceTo(end);
        assertEquals(camp.sim.state().save(), reloaded.state().save());
        assertEquals(camp.sim.ledger().size(), reloaded.ledger().size());
        for (int i = 1; i <= camp.sim.ledger().size(); i++) {
            assertEquals(camp.sim.ledger().get(i).encode(), reloaded.ledger().get(i).encode());
        }
    }
}
