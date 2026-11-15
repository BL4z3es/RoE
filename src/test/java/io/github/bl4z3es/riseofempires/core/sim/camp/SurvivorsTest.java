package io.github.bl4z3es.riseofempires.core.sim.camp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.Ledger;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.sim.CampRules;
import io.github.bl4z3es.riseofempires.core.sim.Founding;
import io.github.bl4z3es.riseofempires.core.sim.Simulation;
import io.github.bl4z3es.riseofempires.core.sim.WorldState;
import java.util.List;
import org.junit.jupiter.api.Test;

class SurvivorsTest {
    @Test
    void threeSurvivorsSetOutAtNightfallAndJoinWithACause() {
        Camp camp = new Camp(7);
        Event setOut = camp.events(Survivors.SET_OUT).get(0);
        assertEquals(Balance.NIGHT_START_HOUR, setOut.hour());
        assertEquals(List.of(Founding.foundingEvent(camp.sim.ledger(), camp.id)), setOut.causes());

        List<Event> joined = camp.events(Survivors.JOINED);
        assertEquals(Balance.STARTING_SURVIVORS, joined.size());
        for (Event e : joined) {
            assertEquals(List.of(setOut.id()), e.causes());
            assertEquals(Balance.NIGHT_START_HOUR + Balance.SURVIVOR_WALK_HOURS, e.hour());
        }
        assertEquals(Balance.STARTING_SURVIVORS, camp.people().size());
        for (Character c : camp.people()) {
            assertFalse(c.name().isEmpty());
            assertFalse(c.trait().isEmpty());
            assertFalse(c.want().isEmpty());
            assertEquals(2, c.memories().size());
        }
        assertEquals(3, camp.people().stream().map(Character::name).distinct().count());
        assertEquals(Balance.STARTING_SURVIVORS * Balance.SURVIVOR_BREAD, camp.stock(Food.BREAD));
        assertEquals(Balance.STARTING_SURVIVORS * Balance.SURVIVOR_SEEDS, camp.stock(Food.SEEDS));
    }

    @Test
    void inTheEmbodiedTierTheyWaitForTheWorldToLetThemIn() {
        Simulation sim = CampRules.install(new Simulation(new WorldState(7), new Ledger()));
        Founding.found(sim, "p1", "Ada", Place.overworld(0, 64, 0));
        String id = sim.state().settlements().iterator().next().id();
        sim.state().setEmbodied(id, true);
        sim.advanceTo(40);
        List<Character> waiting = Survivors.waiting(sim.state(), id);
        assertEquals(3, waiting.size());
        assertTrue(sim.state().residents(id).isEmpty());

        Survivors survivors = CampRules.survivors(sim);
        survivors.admit(sim, id, waiting.get(0).id());
        survivors.turnAway(sim, id, waiting.get(1).id());
        assertEquals(1, sim.state().residents(id).size());
        assertEquals(Character.Status.LEFT, sim.state().character(waiting.get(1).id()).status());
        assertEquals(1, Survivors.waiting(sim.state(), id).size());
        assertEquals(null, survivors.admit(sim, id, waiting.get(1).id()));
    }

    @Test
    void sameSeedSamePeopleDifferentSeedDifferentPeople() {
        Camp a = new Camp(11), b = new Camp(11), c = new Camp(12);
        assertEquals(names(a), names(b));
        assertEquals(a.sim.state().save(), b.sim.state().save());
        assertNotEquals(names(a), names(c));
    }

    private static List<String> names(Camp camp) {
        return camp.people().stream().map(Character::name).toList();
    }
}
