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

    // ---- M1 camp rules. All PROPOSED and untested. ----
    // Time of day: hour 0 is 06:00 (Minecraft day time 0); one in-game hour is 1000 ticks.
    /** 19:00. Survivors set out at the first nightfall after founding. */
    public static final int NIGHT_START_HOUR = 13;
    /** 07:00. Breakfast. Further meals are spaced evenly through the day. */
    public static final int MEAL_FIRST_HOUR = 1;
    /** 00:00. The night's sleep is counted at this hour. */
    public static final int SLEEP_HOUR = 18;

    // Survivors
    /** Hours from setting out to reaching the camp (ledger tier). */
    public static final int SURVIVOR_WALK_HOURS = 2;
    /** Bread each survivor carries in with them. */
    public static final int SURVIVOR_BREAD = 4;
    /** Wheat seeds each survivor carries in with them. */
    public static final int SURVIVOR_SEEDS = 8;

    // Food: meal value of one item. A person needs 1.0 per meal (more in winter).
    public static final double MEAL_VALUE_BREAD = 1.0;
    public static final double MEAL_VALUE_COOKED = 1.0;
    public static final double MEAL_VALUE_PRODUCE = 0.5;
    /** Three wheat make one bread in vanilla, so one wheat is a third of a meal. */
    public static final double MEAL_VALUE_WHEAT = 1.0 / 3.0;

    // Needs. Hunger counts missed meals, fatigue counts nights without a bed.
    public static final int HUNGER_WEAK = 2;
    public static final int HUNGER_ILL = 6;
    public static final int HUNGER_FATAL = 10;
    public static final int FATIGUE_WEAK = 3;
    public static final int FATIGUE_ILL = 7;
    public static final int FATIGUE_FATAL = 12;
    /** Hunger removed by one full meal. */
    public static final int MEAL_HUNGER_RECOVERY = 1;
    /** Work output of a weakened person, as a share of normal. */
    public static final double WEAK_OUTPUT = 0.6;
    /** Work output of an ill person, as a share of normal. */
    public static final double ILL_OUTPUT = 0.25;

    // Farming
    /** Crops one farmer tends: roughly a 5x5 plot. */
    public static final int FARM_PLOT_CROPS = 24;
    /** Hours of normal growing weather from planting to ripe wheat. */
    public static final int CROP_GROWTH_HOURS = 48;
    public static final int PLANT_HOURS = 2;
    /** Harvesting and carrying the crop to the granary. */
    public static final int HARVEST_HOURS = 3;
    public static final double HARVEST_WHEAT_PER_CROP = 1.0;
    public static final double HARVEST_SEEDS_PER_CROP = 1.5;

    // Gathering
    public static final int GATHER_TRIP_HOURS = 3;
    public static final int GATHER_WOOD_PER_TRIP = 6;
    public static final int GATHER_STONE_PER_TRIP = 12;

    // Building
    public static final int BUILD_BLOCKS_PER_HOUR = 8;
    /** Most blocks in one blueprint step: one trip from the granary to the site. */
    public static final int BLUEPRINT_STEP_BLOCKS = 16;

    // Standing, first version (CONTEXT.md section 2)
    public static final double STANDING_POPULATION_WEIGHT = 0.30;
    public static final double STANDING_TERRITORY_WEIGHT = 0.25;
    public static final int BLOCKS_PER_CHUNK = 256;

    // Raids
    /** Whole days after founding before the Wilds plan the first raid. */
    public static final int RAID_FIRST_DAY = 8;
    /** 08:00. Hour of day a raid is planned. */
    public static final int RAID_PLAN_HOUR = 2;
    /** Hours between the hidden plan and the first sign the camp can see. */
    public static final int RAID_ANNOUNCE_DELAY_HOURS = 2;
    /** Hours between the warning and the raiders reaching the camp. */
    public static final int RAID_WARNING_HOURS = 24;
    /** A watched fight not reported by the world after this long is settled by the calculator. */
    public static final int RAID_MAX_BATTLE_HOURS = 6;
    /** T1 raid size = this times Standing, clamped to the T1 range. */
    public static final double T1_RAID_BASE = 3.0;
    /** Very low health: one raider is half an unarmed villager. */
    public static final double RAID_RAIDER_STRENGTH = 0.5;
    public static final double RAID_RESIDENT_STRENGTH = 1.0;
    public static final double RAID_FIGHTER_STRENGTH = 3.0;
    /** Food items each surviving raider carries off. */
    public static final int RAID_FOOD_PER_RAIDER = 6;
    /** Raiders this many times stronger than the defence also kill one defender. */
    public static final double RAID_KILL_RATIO = 2.0;
}
