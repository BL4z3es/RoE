package io.github.bl4z3es.riseofempires.core.sim;

import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;

/**
 * Emits {@link #DAY_STARTED} at the first hour of each day and {@link #SEASON_CHANGED} when a
 * new season begins. Other systems use these events as causes. Add this system first.
 */
public final class CalendarSystem implements SimSystem {
    public static final String ID = "calendar";
    public static final String DAY_STARTED = "day_started";
    public static final String SEASON_CHANGED = "season_changed";

    private Event lastSeasonChange;
    private Event lastDayStart;

    @Override
    public String id() { return ID; }

    @Override
    public void hourly(Simulation sim) {
        long hour = sim.state().hour();
        if (Calendar.hourOfDay(hour) != 0) return;
        Calendar.Season season = Calendar.season(hour);
        if (hour > 0 && season != Calendar.season(hour - 1)) {
            lastSeasonChange = sim.emit(EventDraft.of(SEASON_CHANGED)
                    .actor(Ref.THE_WORLD)
                    .causedBy(seasonEvent(sim))
                    .summary(name(season) + " began, year " + (Calendar.year(hour) + 1) + "."));
        }
        lastDayStart = sim.emit(EventDraft.of(DAY_STARTED)
                .actor(Ref.THE_WORLD)
                .causedBy(seasonEvent(sim))
                .summary("Day " + (Calendar.day(hour) + 1) + " began (" + name(season).toLowerCase() + ")."));
    }

    /** The event that began the current season, or null in the world's first season. */
    public Event seasonEvent(Simulation sim) {
        if (lastSeasonChange == null) lastSeasonChange = sim.ledger().latest(SEASON_CHANGED);
        return lastSeasonChange;
    }

    /** The event that began the current day, or null before the first day boundary. */
    public Event dayEvent(Simulation sim) {
        if (lastDayStart == null) lastDayStart = sim.ledger().latest(DAY_STARTED);
        return lastDayStart;
    }

    private static String name(Calendar.Season season) {
        String s = season.name();
        return s.charAt(0) + s.substring(1).toLowerCase();
    }
}
