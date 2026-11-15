package io.github.bl4z3es.riseofempires.core.sim.camp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.model.Professions;
import java.util.List;
import org.junit.jupiter.api.Test;

class GuidanceTest {
    @Test
    void aNewCampHearsWhatItNeedsMostUrgentFirst() {
        Camp camp = new Camp(4);
        List<Advice> advice = Guidance.advise(camp.sim, camp.id);
        List<String> topics = advice.stream().map(Advice::topic).toList();
        assertTrue(topics.contains("beds"));
        assertTrue(topics.contains("farmer"));
        assertTrue(topics.contains("idle"));
        for (int i = 1; i < advice.size(); i++) assertTrue(advice.get(i - 1).priority() >= advice.get(i).priority());
        for (Advice a : advice) {
            String trait = camp.sim.state().character(a.speakerId()).trait();
            assertTrue(a.text().startsWith(SurvivorTables.trait(trait).voice()), a.text());
        }
    }

    @Test
    void anAnnouncedRaidComesFirstAndPointsAtItsEvent() {
        Camp camp = new Camp(4);
        camp.beds(3);
        camp.addStock(Food.BREAD, 300);
        for (int i = 0; i < 3; i++) camp.assign(i, Professions.FARMER);
        camp.runToDay(Balance.RAID_FIRST_DAY);
        camp.runDays(0.25);
        Advice first = Guidance.advise(camp.sim, camp.id).get(0);
        assertEquals("raid", first.topic());
        assertEquals(Raids.ANNOUNCED, camp.sim.ledger().get(first.basedOn()).type());
    }
}
