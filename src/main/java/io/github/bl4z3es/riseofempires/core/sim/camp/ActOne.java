package io.github.bl4z3es.riseofempires.core.sim.camp;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.sim.Calendar;
import io.github.bl4z3es.riseofempires.core.sim.CalendarSystem;
import io.github.bl4z3es.riseofempires.core.sim.Effects;
import io.github.bl4z3es.riseofempires.core.sim.SimSystem;
import io.github.bl4z3es.riseofempires.core.sim.Simulation;
import io.github.bl4z3es.riseofempires.core.sim.WorldState;
import java.util.ArrayList;
import java.util.List;

/**
 * The test of Act I (task M1-21). A survivor warns of winter a season ahead. The first winter
 * that begins after people arrive is the test: reach spring with anyone alive and Act I is won.
 * A settlement that once had people and has none left is lost, at any time.
 */
public final class ActOne implements SimSystem {
    public static final String ID = "act_one";

    public static final String WINTER_WARNING = "winter_warning";
    public static final String WINTER_CAME = "first_winter_came";
    public static final String SURVIVED = "act_one_survived";
    public static final String CAMP_LOST = "camp_lost";

    /** Settlement tags. */
    public static final String WARNED = "act1.warned";
    public static final String WINTER = "act1.winter";
    /** "", {@link #OUTCOME_SURVIVED} or {@link #OUTCOME_LOST}. */
    public static final String OUTCOME = "act1.outcome";
    public static final String LOST = "lost";
    public static final String OUTCOME_SURVIVED = "survived";
    public static final String OUTCOME_LOST = "lost";

    /** Events that can decide the fate of a camp, searched for when explaining an ending. */
    private static final List<String> DECISIVE = List.of(People.DEATH, People.LEFT, Raids.RESOLVED,
            Needs.FOOD_RAN_OUT, Needs.FELL_ILL, BuildSites.FINISHED);

    @Override
    public String id() { return ID; }

    @Override
    public void hourly(Simulation sim) {
        WorldState state = sim.state();
        long hour = state.hour();
        CalendarSystem cal = (CalendarSystem) sim.system(CalendarSystem.ID);
        Event seasonEvent = cal == null ? null : cal.seasonEvent(sim);
        boolean dayStart = Calendar.hourOfDay(hour) == 0;
        boolean seasonStart = dayStart && seasonEvent != null && seasonEvent.hour() == hour;
        Calendar.Season season = Calendar.season(hour);

        for (Settlement s : List.copyOf(state.settlements())) {
            Ref sref = Ref.settlement(s.id());
            if (state.stat(sref, Survivors.EVER_PEOPLED) == 0) continue;
            List<Character> people = state.residents(s.id());
            if (people.isEmpty()) {
                if (state.tag(sref, LOST).isEmpty()) lose(sim, s);
                continue;
            }
            if (!state.tag(sref, OUTCOME).isEmpty() || !dayStart) continue;

            if (season == Calendar.Season.AUTUMN && state.tag(sref, WARNED).isEmpty()) {
                warn(sim, s, people, seasonEvent);
            } else if (seasonStart && season == Calendar.Season.WINTER && state.tag(sref, WINTER).isEmpty()) {
                long id = sim.ledger().size() + 1L;
                sim.emit(EventDraft.of(WINTER_CAME).at(s.centre()).actor(sref).causedBy(seasonEvent)
                        .cause(parse(state.tag(sref, WARNED)))
                        .effect(Effects.setTag(sref, WINTER, String.valueOf(id)))
                        .summary("The first winter has come to " + s.name() + ": nothing will grow until spring, "
                                + "and every meal costs a quarter more."));
            } else if (seasonStart && season == Calendar.Season.SPRING && !state.tag(sref, WINTER).isEmpty()) {
                EventDraft d = EventDraft.of(SURVIVED).at(s.centre()).actor(sref).causedBy(seasonEvent)
                        .cause(parse(state.tag(sref, WINTER)))
                        .effect(Effects.setTag(sref, OUTCOME, OUTCOME_SURVIVED));
                for (long c : decisive(sim, s.id(), parse(state.tag(sref, WINTER)))) d.cause(c);
                List<String> names = new ArrayList<>();
                for (Character c : people) { d.actor(Ref.character(c.id())); names.add(c.name()); }
                sim.emit(d.summary(s.name() + " lived through its first winter. " + String.join(", ", names)
                        + (names.size() == 1 ? " saw" : " saw") + " the spring. Act I is over."));
            }
        }
    }

    private void warn(Simulation sim, Settlement s, List<Character> people, Event seasonEvent) {
        WorldState state = sim.state();
        Ref sref = Ref.settlement(s.id());
        Character speaker = people.get(0);
        int days = Calendar.daysUntilNextSeason(state.hour());
        double need = Needs.dailyNeed(state, s.id(), Calendar.Season.WINTER) * Balance.SEASON_LENGTH_DAYS;
        double have = Food.meals(state.stockpile(s.stockpileId()));
        long id = sim.ledger().size() + 1L;
        EventDraft d = EventDraft.of(WINTER_WARNING).at(s.centre()).actor(sref).actor(Ref.character(speaker.id()))
                .causedBy(seasonEvent)
                .effect(Effects.setTag(sref, WARNED, String.valueOf(id)))
                .summary(speaker.name() + " warned that winter comes in " + days + " days: " + (int) Math.ceil(need)
                        + " meals must be stored to feed everyone through it, and the granary holds "
                        + (int) Math.floor(have) + ".");
        for (Character c : people) d.witness(Ref.character(c.id()));
        sim.emit(d);
    }

    private void lose(Simulation sim, Settlement s) {
        Ref sref = Ref.settlement(s.id());
        EventDraft d = EventDraft.of(CAMP_LOST).at(s.centre()).actor(sref)
                .effect(Effects.setTag(sref, LOST, "1"));
        if (sim.state().tag(sref, OUTCOME).isEmpty()) d.effect(Effects.setTag(sref, OUTCOME, OUTCOME_LOST));
        for (long c : decisive(sim, s.id(), 0)) d.cause(c);
        sim.emit(d.summary(s.name() + " is lost: nobody is left living there."));
    }

    /**
     * The events that decided this camp's fate since {@code sinceId}: every death and departure,
     * the raid, the food running out, illness and finished buildings. Newest last.
     */
    private static List<Long> decisive(Simulation sim, String settlementId, long sinceId) {
        Ref sref = Ref.settlement(settlementId);
        List<Long> out = new ArrayList<>();
        for (Event e : sim.ledger().all()) {
            if (e.id() <= sinceId || !DECISIVE.contains(e.type())) continue;
            if (e.actors().contains(sref)) { out.add(e.id()); continue; }
            if (e.type().equals(Needs.FELL_ILL)) {
                for (Ref a : e.actors()) {
                    Character c = sim.state().character(a.id());
                    if (a.kind().equals(Ref.CHARACTER) && c != null && settlementId.equals(c.settlementId())) out.add(e.id());
                }
            }
        }
        return out;
    }

    private static long parse(String s) {
        return s.isEmpty() ? 0 : Long.parseLong(s);
    }
}
