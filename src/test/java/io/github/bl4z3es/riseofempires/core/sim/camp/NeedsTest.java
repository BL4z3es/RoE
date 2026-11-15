package io.github.bl4z3es.riseofempires.core.sim.camp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.sim.Calendar;
import io.github.bl4z3es.riseofempires.core.sim.CalendarSystem;
import java.util.List;
import org.junit.jupiter.api.Test;

class NeedsTest {
    @Test
    void mealHoursMatchTheMealsPerDay() {
        long count = java.util.stream.IntStream.range(0, Balance.HOURS_PER_DAY).filter(Needs::isMealHour).count();
        assertEquals(Balance.MEALS_PER_PERSON_PER_DAY, count);
        assertEquals(Balance.WINTER_FOOD_MULTIPLIER, Needs.mealNeed(Calendar.Season.WINTER));
    }

    @Test
    void fedPeopleEatFromTheGranary() {
        Camp camp = new Camp(3);
        camp.beds(3);
        int before = camp.stock(Food.BREAD);
        camp.runDays(1);
        assertEquals(before - 3 * Balance.MEALS_PER_PERSON_PER_DAY, camp.stock(Food.BREAD));
        for (Character c : camp.people()) assertEquals(0.0, camp.sim.state().stat(Ref.character(c.id()), Needs.HUNGER));
    }

    @Test
    void starvationDeclinesStepByStepAndEveryDeathHasAWalkableCause() {
        Camp camp = new Camp(3);
        camp.beds(3);
        camp.emptyGranary();
        camp.runDays(8);

        assertTrue(camp.people().isEmpty(), "nobody survives eight days without food");
        List<Event> endings = camp.sim.ledger().all().stream()
                .filter(e -> e.type().equals(People.DEATH) || e.type().equals(People.LEFT)).toList();
        assertEquals(3, endings.size());
        for (Event end : endings) {
            assertTrue(camp.chainHas(end, Needs.FELL_ILL), "ill before the end");
            assertTrue(camp.chainHas(end, Needs.WEAKENED), "weak before ill");
            assertTrue(camp.chainHas(end, Needs.WENT_HUNGRY));
            assertTrue(camp.chainHas(end, Needs.FOOD_RAN_OUT));
            assertTrue(camp.chainHas(end, CalendarSystem.DAY_STARTED));
            Character c = camp.sim.state().character(end.actors().get(0).id());
            boolean leaves = SurvivorTables.trait(c.trait()).leavesWhenDesperate();
            assertEquals(leaves ? People.LEFT : People.DEATH, end.type());
            assertEquals(leaves ? Character.Status.LEFT : Character.Status.DEAD, c.status());
        }
    }

    @Test
    void hungryPeopleWorkLessAndRecoverWhenFed() {
        Camp camp = new Camp(3);
        camp.beds(3);
        camp.emptyGranary();
        camp.runDays(1.5);
        String id = camp.people().get(0).id();
        assertEquals(Needs.WEAK, camp.sim.state().tag(Ref.character(id), Needs.CONDITION));
        assertEquals(Balance.WEAK_OUTPUT, Needs.outputMultiplier(camp.sim.state(), id));
        camp.addStock(Food.BREAD, 100);
        camp.runDays(2);
        assertEquals("", camp.sim.state().tag(Ref.character(id), Needs.CONDITION));
        assertFalse(camp.events(Needs.RECOVERED).isEmpty());
    }

    @Test
    void nightsWithoutBedsWearPeopleDown() {
        Camp camp = new Camp(3);
        camp.addStock(Food.BREAD, 500);
        camp.beds(1);
        camp.runDays(Balance.FATIGUE_WEAK + 1);
        List<Character> people = camp.people();
        assertEquals("", camp.sim.state().tag(Ref.character(people.get(0).id()), Needs.CONDITION));
        assertEquals(Needs.WEAK, camp.sim.state().tag(Ref.character(people.get(2).id()), Needs.CONDITION));
        Event weak = camp.events(Needs.WEAKENED).get(0);
        assertTrue(camp.chainHas(weak, Needs.SLEPT_ROUGH));
    }
}
