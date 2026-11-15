package io.github.bl4z3es.riseofempires.core.sim.camp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.model.Professions;
import io.github.bl4z3es.riseofempires.core.sim.CalendarSystem;
import java.util.List;
import org.junit.jupiter.api.Test;

class ActOneTest {
    @Test
    void aWellRunCampIsWarnedSurvivesTheRaidAndTheWinterAndWinsActOne() {
        Camp camp = new Camp(21);
        camp.beds(3);
        camp.assign(0, Professions.FARMER);
        camp.assign(1, Professions.GATHERER);
        camp.assign(2, Professions.FIGHTER);
        camp.addStock(Food.BREAD, 120);
        camp.runToDay(Balance.YEAR_LENGTH_DAYS + 1);

        Event warning = camp.events(ActOne.WINTER_WARNING).get(0);
        Event winter = camp.events(ActOne.WINTER_CAME).get(0);
        assertEquals(Balance.SEASON_LENGTH_DAYS * 24, winter.hour() - warning.hour(), "warned a season ahead");
        assertTrue(winter.causes().contains(warning.id()));
        assertTrue(camp.events(Raids.RESOLVED).get(0).summary().contains("driven off"));

        Event won = camp.events(ActOne.SURVIVED).get(0);
        assertTrue(won.causes().contains(winter.id()));
        assertTrue(camp.chainHas(won, CalendarSystem.SEASON_CHANGED));
        assertTrue(camp.events(ActOne.CAMP_LOST).isEmpty());
        assertFalse(camp.people().isEmpty());
    }

    @Test
    void aCampThatStarvesIsLostAndSaysWhy() {
        Camp camp = new Camp(21);
        camp.emptyGranary();
        camp.runDays(10);
        List<Event> lost = camp.events(ActOne.CAMP_LOST);
        assertEquals(1, lost.size());
        Event end = lost.get(0);
        long endings = end.causes().stream().map(camp.sim.ledger()::get)
                .filter(e -> e.type().equals(People.DEATH) || e.type().equals(People.LEFT)).count();
        assertEquals(3, endings, "every death and departure is a cause");
        assertTrue(end.causes().stream().map(camp.sim.ledger()::get).anyMatch(e -> e.type().equals(Needs.FOOD_RAN_OUT)));
        assertTrue(camp.chainHas(end, Needs.WENT_HUNGRY));
        assertTrue(camp.events(ActOne.SURVIVED).isEmpty());
    }
}
