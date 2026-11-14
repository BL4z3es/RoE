package io.github.bl4z3es.riseofempires.core;

/**
 * The single home for balance numbers (rule E25). Every value here is listed in the numbers
 * table in CONTEXT.md section 5. All are PROPOSED and untested.
 */
public final class Balance {
    private Balance() {}

    // Calendar
    public static final int SEASON_LENGTH_DAYS = 6;
    public static final int YEAR_LENGTH_DAYS = 24;
    public static final int HOURS_PER_DAY = 24;

    // Needs and farming
    public static final double WINTER_FOOD_MULTIPLIER = 1.25;
    public static final double SPRING_GROWTH_MULTIPLIER = 1.25;
    public static final int MEALS_PER_PERSON_PER_DAY = 2;

    // Founding
    public static final int STARTING_CLAIM_RADIUS_BLOCKS = 32;
    public static final int STARTING_SURVIVORS = 3;

    // Simulation tiers
    public static final int EMBODIED_RADIUS_BLOCKS = 48;
    public static final int LEDGER_UPDATE_INTERVAL_HOURS = 1;

    // War
    public static final int EMBODIED_FIGHTERS_PER_SIDE = 120;

    // World
    public static final int KNOWN_WORLD_SIZE_BLOCKS = 12_000;
    public static final int HISTORY_LENGTH_YEARS = 200;

    // Threat tiers (min, max group size)
    public static final int T1_RAID_MIN = 4;
    public static final int T1_RAID_MAX = 10;
    public static final int T2_RAID_MIN = 15;
    public static final int T2_RAID_MAX = 40;
    public static final int T3_ARMY_MIN = 100;
    public static final int T3_ARMY_MAX = 300;
    public static final int T4_ARMY_MIN = 500;
    public static final int T4_ARMY_MAX = 1_000;
}
