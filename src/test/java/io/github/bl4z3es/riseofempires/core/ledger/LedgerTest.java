package io.github.bl4z3es.riseofempires.core.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class LedgerTest {
    @Test
    void chainCanBeWrittenReloadedAndWalkedBackToItsRoots() {
        Ledger ledger = new Ledger();
        Event winter = ledger.append(EventDraft.of("season_changed").summary("Winter began."), 10);
        Event empty = ledger.append(EventDraft.of("stockpile_empty").summary("The granary | ran, out\nof food."), 12);
        Event hungry = ledger.append(EventDraft.of("went_hungry").causedBy(winter).causedBy(empty)
                .actor(Ref.character("c1")).witness(Ref.character("c2")).at(Place.overworld(1, 64, -3))
                .effect(new Effect("stat.add", Ref.character("c1"), "hunger", 1.5, "")), 13);
        Event death = ledger.append(EventDraft.of("death").causedBy(hungry), 40);

        Ledger reloaded = new Ledger();
        for (Event e : ledger.all()) reloaded.restore(Event.decode(e.encode()));

        assertEquals(ledger.all(), reloaded.all());
        assertEquals(List.of(hungry, winter, empty), reloaded.causeChain(death.id()));
        assertEquals(List.of(winter, empty), reloaded.roots(death.id()));
        assertEquals(List.of(), reloaded.causeChain(winter.id()));
    }

    @Test
    void anEventCannotNameACauseThatHasNotHappened() {
        Ledger ledger = new Ledger();
        assertThrows(IllegalArgumentException.class, () -> ledger.append(EventDraft.of("x").cause(5), 0));
    }

    @Test
    void listenersSeeEveryAppend() {
        Ledger ledger = new Ledger();
        int[] seen = {0};
        ledger.onAppend(e -> seen[0]++);
        ledger.append(EventDraft.of("a"), 0);
        ledger.append(EventDraft.of("b"), 0);
        assertEquals(2, seen[0]);
        assertEquals("b", ledger.recent(1).get(0).type());
    }
}
