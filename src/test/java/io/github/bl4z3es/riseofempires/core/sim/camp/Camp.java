package io.github.bl4z3es.riseofempires.core.sim.camp;

import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.Ledger;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.sim.Calendar;
import io.github.bl4z3es.riseofempires.core.sim.CampRules;
import io.github.bl4z3es.riseofempires.core.sim.Effects;
import io.github.bl4z3es.riseofempires.core.sim.Founding;
import io.github.bl4z3es.riseofempires.core.sim.Simulation;
import io.github.bl4z3es.riseofempires.core.sim.WorldState;
import java.util.List;

/** Test fixture: a camp founded at dawn on day 1 with its three survivors already in. */
final class Camp {
    final Simulation sim;
    final String id;

    Camp(long seed) {
        sim = CampRules.install(new Simulation(new WorldState(seed), new Ledger()));
        Founding.found(sim, "p1", "Ada", Place.overworld(0, 64, 0));
        id = sim.state().settlements().iterator().next().id();
        sim.advanceTo(16);
    }

    Settlement settlement() { return sim.state().settlement(id); }
    List<Character> people() { return sim.state().residents(id); }
    Jobs jobs() { return CampRules.jobs(sim); }
    int stock(String item) { return sim.state().stockpile(settlement().stockpileId()).count(item); }

    /** Test-only stock change, recorded like any other event. */
    void addStock(String item, int n) {
        sim.emit(EventDraft.of("test_stock").effect(Effects.addStock(settlement().stockpileId(), item, n)));
    }

    void emptyGranary() {
        for (String item : Food.MEAL_VALUES.keySet()) if (stock(item) > 0) addStock(item, -stock(item));
    }

    void beds(int n) { Needs.countBeds(sim, id, n, 0); }

    void assign(int index, String profession) { jobs().assign(sim, people().get(index).id(), profession, 0); }

    void runDays(double days) { sim.advanceTo(sim.state().hour() + Math.round(days * 24)); }

    void runToDay(long day) { sim.advanceTo(Calendar.hourOfDayStart(day)); }

    List<Event> events(String type) {
        return sim.ledger().all().stream().filter(e -> e.type().equals(type)).toList();
    }

    boolean chainHas(Event e, String type) {
        return sim.ledger().causeChain(e.id()).stream().anyMatch(c -> c.type().equals(type));
    }

    Ref ref() { return Ref.settlement(id); }
}
