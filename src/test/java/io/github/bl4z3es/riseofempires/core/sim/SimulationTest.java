package io.github.bl4z3es.riseofempires.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.ledger.Effect;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.Ledger;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Nation;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimulationTest {
    private static Simulation sim() {
        return new Simulation(new WorldState(42), new Ledger()).add(new CalendarSystem());
    }

    @Test
    void daysMapToSeasonsAndYears() {
        assertEquals(Calendar.Season.SPRING, Calendar.season(0));
        assertEquals(Calendar.Season.SUMMER, Calendar.season(Calendar.hourOfDayStart(6)));
        assertEquals(Calendar.Season.WINTER, Calendar.season(Calendar.hourOfDayStart(23) + 23));
        assertEquals(Calendar.Season.SPRING, Calendar.season(Calendar.hourOfDayStart(24)));
        assertEquals(1, Calendar.year(Calendar.hourOfDayStart(24)));
        assertEquals(6, Calendar.daysUntilNextSeason(0));
        assertEquals(1, Calendar.daysUntilNextSeason(Calendar.hourOfDayStart(5) + 23));
    }

    @Test
    void aSeasonChangeAppearsInTheLedgerAndCausesTheDaysInIt() {
        Simulation sim = sim();
        sim.advanceTo(Calendar.hourOfDayStart(Balance.SEASON_LENGTH_DAYS) + 30);
        List<Event> changes = sim.ledger().all().stream()
                .filter(e -> e.type().equals(CalendarSystem.SEASON_CHANGED)).toList();
        assertEquals(1, changes.size());
        assertEquals("Summer began, year 1.", changes.get(0).summary());
        Event lastDay = sim.ledger().latest(CalendarSystem.DAY_STARTED);
        assertEquals(List.of(changes.get(0).id()), lastDay.causes());
    }

    @Test
    void foundingCreatesNationAndSettlementOnceAndRefusesOverlaps() {
        Simulation sim = sim();
        Event founded = Founding.found(sim, "p1", "Ada", Place.overworld(100, 70, 100));
        assertNotNull(founded);
        Nation nation = Founding.nationOf(sim.state(), "p1");
        assertTrue(nation.playerRuled());
        Settlement camp = Founding.settlementAt(sim.state(), Place.overworld(110, 64, 110));
        assertEquals("camp", camp.rank());
        assertEquals(nation.id(), camp.nationId());
        assertEquals(Balance.STARTING_CLAIM_RADIUS_BLOCKS, camp.claimRadius());
        assertNull(Founding.found(sim, "p1", "Ada", Place.overworld(105, 70, 100)));
        assertEquals(1, sim.state().settlements().size());
    }

    @Test
    void stateChangesOnlyThroughEventsAndSurvivesSaveAndLoad() {
        Simulation sim = sim();
        Founding.found(sim, "p1", "Ada", Place.overworld(0, 64, 0));
        Settlement camp = sim.state().settlements().iterator().next();
        Character mara = new Character("c1", "Mara", "stubborn", "a roof of her own", "",
                List.of("Saw the granary burn | at Eastmere"), "", Character.Status.ALIVE);
        sim.emit(EventDraft.of("arrived").actor(Ref.character("c1"))
                .effect(Effects.putCharacter(mara))
                .effect(Effects.addResident(camp.id(), "c1"))
                .effect(Effects.setProfession("c1", "farmer"))
                .effect(Effects.addStat(Ref.character("c1"), "hunger", 2))
                .effect(Effects.setTag(Ref.settlement(camp.id()), "mood", "wary, hopeful"))
                .effect(Effects.addStock(camp.stockpileId(), "minecraft:wheat", 12))
                .effect(Effects.addStock(camp.stockpileId(), "minecraft:wheat", -5)));

        WorldState s = sim.state();
        assertEquals(List.of("c1"), s.settlement(camp.id()).residents());
        assertEquals(camp.id(), s.character("c1").settlementId());
        assertEquals("farmer", s.character("c1").profession());
        assertEquals(2.0, s.stat(Ref.character("c1"), "hunger"));
        assertEquals(7, s.stockpile(camp.stockpileId()).count("minecraft:wheat"));
        assertEquals(1, s.residents(camp.id()).size());

        WorldState loaded = WorldState.load(s.save());
        assertEquals(s.save(), loaded.save());
        assertEquals(mara.memories(), loaded.character("c1").memories());
        assertEquals("wary, hopeful", loaded.tag(Ref.settlement(camp.id()), "mood"));
        assertEquals(s.hour(), loaded.hour());
    }

    @Test
    void anEffectWithNoHandlerIsRejectedBeforeAnythingIsRecorded() {
        Simulation sim = sim();
        assertThrows(IllegalArgumentException.class, () -> sim.emit(
                EventDraft.of("x").effect(new Effect("no.such.kind", Ref.THE_WORLD, "", 0, ""))));
        assertEquals(0, sim.ledger().size());
    }

    @Test
    void sameSeedAndHourGiveTheSameRolls() {
        Simulation a = sim(), b = sim();
        a.advanceTo(5);
        b.advanceTo(5);
        assertEquals(a.rng("raids").nextInt(1000), b.rng("raids").nextInt(1000));
    }
}
