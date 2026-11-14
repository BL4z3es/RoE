package io.github.bl4z3es.riseofempires.core.sim;

import io.github.bl4z3es.riseofempires.core.Balance;

/** Maps hours to days, seasons and years. The world begins at hour 0: day 0, spring, year 0. */
public final class Calendar {
    private Calendar() {}

    public enum Season { SPRING, SUMMER, AUTUMN, WINTER }

    public static long day(long hour) {
        return hour / Balance.HOURS_PER_DAY;
    }

    public static int hourOfDay(long hour) {
        return (int) (hour % Balance.HOURS_PER_DAY);
    }

    public static long year(long hour) {
        return day(hour) / Balance.YEAR_LENGTH_DAYS;
    }

    public static int dayOfYear(long hour) {
        return (int) (day(hour) % Balance.YEAR_LENGTH_DAYS);
    }

    public static Season season(long hour) {
        return Season.values()[dayOfYear(hour) / Balance.SEASON_LENGTH_DAYS];
    }

    public static Season next(Season season) {
        return Season.values()[(season.ordinal() + 1) % 4];
    }

    /** Whole days until the next season begins. Always at least 1. */
    public static int daysUntilNextSeason(long hour) {
        return Balance.SEASON_LENGTH_DAYS - dayOfYear(hour) % Balance.SEASON_LENGTH_DAYS;
    }

    /** The first hour of the given day. */
    public static long hourOfDayStart(long day) {
        return day * Balance.HOURS_PER_DAY;
    }
}
