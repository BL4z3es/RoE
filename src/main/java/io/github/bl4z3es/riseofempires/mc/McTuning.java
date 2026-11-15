package io.github.bl4z3es.riseofempires.mc;

/**
 * Numbers that only the Minecraft layer uses: timings, distances and budgets for bodies and
 * blocks. Balance numbers that the rules read live in {@code core.Balance} (rule E25).
 */
public final class McTuning {
    private McTuning() {}

    /** Game ticks in one in-game hour of the simulation (24 000 ticks per day). */
    public static final int TICKS_PER_HOUR = 1000;
    /** Most wall-clock time the simulation may use in one server tick to catch up (rule E9). */
    public static final long SIM_BUDGET_NANOS_PER_TICK = 2_000_000L;
    /** How often settlements are checked for nearby players. */
    public static final int EMBODIED_CHECK_TICKS = 20;
    /** How often real containers are compared with the stockpile record. */
    public static final int STOCKPILE_SYNC_TICKS = 40;
    /** Granary Seal reach: containers within this many blocks belong to its stockpile. */
    public static final int GRANARY_RADIUS = 6;
    /** Ticks between two blocks of a building going up while someone watches. */
    public static final int BUILD_TICKS_PER_BLOCK = 5;
    /** Bodies never appear or vanish closer than this to a player (law L1). */
    public static final int OUT_OF_SIGHT_BLOCKS = 40;
    /** How far from the camp centre an arriving survivor or raider starts walking. */
    public static final int ARRIVAL_DISTANCE_BLOCKS = 56;
    /** How close a body must get to a work spot to count as arrived. */
    public static final double REACH_BLOCKS = 2.5;
    /** Walking speed modifier for people on errands. */
    public static final double WALK_SPEED = 0.6;
    /** Ticks a person stands at a work spot to do one action (plant, harvest, lay blocks). */
    public static final int WORK_ACTION_TICKS = 40;
    /** A body that cannot reach its target for this long gives up and tries again later. */
    public static final int STUCK_TICKS = 600;
    /** How often people say what the camp needs, per person. */
    public static final int ADVICE_TICKS = 2400;
    /** Lines of the event log written per flush of the background writer. */
    public static final int LEDGER_FLUSH_LINES = 256;
}
