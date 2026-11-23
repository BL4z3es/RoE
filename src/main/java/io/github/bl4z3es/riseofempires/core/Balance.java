package io.github.bl4z3es.riseofempires.core;

/**
 * Every tunable balance number, in one place (rule E25).
 *
 * <p>Each constant mirrors a row of the numbers table in {@code CONTEXT.md} section 5. All are
 * {@code PROPOSED} starting values and none has been tested. Change a value here and in that table
 * together. Rule code reads these names and never repeats the number.
 *
 * <p>Only the rows Milestone 1 uses are here. Later rows are added by the task that first needs them.
 */
public final class Balance {
	private Balance() {}

	// Calendar

	/** In-game days in one season. */
	public static final int SEASON_LENGTH_DAYS = 6;

	/** In-game days in one year: four seasons. */
	public static final int YEAR_LENGTH_DAYS = 24;

	// Needs and farming

	/** Food use in winter, as a multiple of normal use. */
	public static final double WINTER_FOOD_MULTIPLIER = 1.25;

	/** Crop growth in spring, as a multiple of normal growth. */
	public static final double SPRING_GROWTH_MULTIPLIER = 1.25;

	/** Meals one person eats per in-game day. */
	public static final int MEALS_PER_PERSON_PER_DAY = 2;

	// Founding and the opening

	/** Radius in blocks of a newly founded camp's claim. */
	public static final int STARTING_CLAIM_RADIUS_BLOCKS = 32;

	/** Survivors who arrive on the first night after founding. */
	public static final int STARTING_SURVIVORS = 3;

	// Simulation tiers

	/** Characters within this many blocks of a player are embodied. */
	public static final int EMBODIED_RADIUS_BLOCKS = 48;

	/** In-game hours between updates of characters in the Ledger tier. */
	public static final int LEDGER_UPDATE_INTERVAL_HOURS = 1;

	// Threats

	/** Fewest raiders in a T1 raid. */
	public static final int T1_RAID_SIZE_MIN = 4;

	/** Most raiders in a T1 raid. */
	public static final int T1_RAID_SIZE_MAX = 10;

	// ---------------------------------------------------------------------------------------------
	// Not yet in the CONTEXT.md section 5 table. Claude added these while building M1 because a
	// rule needed a number the design does not give. Each is a guess awaiting the owner. Rows to add
	// to the table are listed in TASKS.md under M1.
	// ---------------------------------------------------------------------------------------------

	/** Crop growth in winter. CONTEXT.md 3.5 says "no outdoor crop growth", so zero. */
	public static final double WINTER_GROWTH_MULTIPLIER = 0.0;

	/** Day ticks (0 = sunrise) when the meals of a day are eaten: breakfast and supper. */
	public static final int[] MEAL_TIMES_OF_DAY = {1000, 12000};

	/** Day tick when the night's sleep is judged: midnight. */
	public static final int NIGHT_TIME_OF_DAY = 18000;

	/** A normal meal, in quarter-meals. Winter meals cost this times WINTER_FOOD_MULTIPLIER. */
	public static final int QUARTERS_PER_MEAL = 4;

	/** Missed meals plus rough nights at which a character becomes strained. */
	public static final int STRAINED_AT = 2;

	/** ...becomes weak. */
	public static final int WEAK_AT = 4;

	/** ...becomes ill and stops working. */
	public static final int ILL_AT = 6;

	/** ...dies or leaves. */
	public static final int GONE_AT = 8;

	/** Chance that a character who reaches GONE_AT leaves rather than dies. */
	public static final double LEAVE_CHANCE = 0.5;

	/** Missed meals forgiven by each meal eaten. */
	public static final int MISSED_MEALS_FORGIVEN_PER_MEAL = 2;

	/** Work output by condition: well, strained, weak, ill. */
	public static final double OUTPUT_WELL = 1.0;
	public static final double OUTPUT_STRAINED = 0.75;
	public static final double OUTPUT_WEAK = 0.5;
	public static final double OUTPUT_ILL = 0.0;

	/** Embodied job timings in ticks, shared by the world entities and the Ledger tier's rates (rule E3). */
	public static final int HARVEST_TICKS = 20;
	public static final int REPLANT_TICKS = 10;
	public static final int CHOP_TICKS = 40;
	public static final int MINE_TICKS = 30;
	public static final int PLACE_BLOCK_TICKS = 20;
	public static final int DEPOSIT_TICKS = 20;
	public static final int TAKE_TICKS = 20;
	/** Ticks a worker needs to walk one block. */
	public static final int WALK_TICKS_PER_BLOCK = 8;
	/** Items a worker carries before walking to the store. */
	public static final int CARRY_CAPACITY = 16;
	/** How far from the camp centre a gatherer looks for trees and stone. */
	public static final int GATHER_RADIUS_BLOCKS = 24;
	/** Average walk between a gatherer's finds, in blocks, used by the Ledger tier. */
	public static final int GATHER_AVERAGE_WALK_BLOCKS = 6;

	/** Base raid size per point of Standing for a T1 raid (CONTEXT.md 2: base(tier)). */
	public static final double T1_RAID_BASE = 3.0;
	/** Days after founding before the first raid can be planned. */
	public static final int RAID_EARLIEST_DAY_AFTER_FOUNDING = 2;
	/** Ticks from planning to the warning (smoke on the horizon). */
	public static final int RAID_SIGHT_DELAY_TICKS = 6000;
	/** Ticks from the warning to the raiders reaching the camp. */
	public static final int RAID_ARRIVAL_DELAY_TICKS = 24000;
	/** Blocks from the camp centre where raiders gather, beyond sight. */
	public static final int RAID_ORIGIN_DISTANCE_BLOCKS = 112;
	/** Share of the store's food a raid carries off if it is not stopped. */
	public static final double RAID_STEAL_FRACTION = 0.5;
	/** Raider health in half-hearts ("very low health"). A zombie has 20. */
	public static final int RAIDER_HEALTH = 6;

	/** Blocks from world spawn within which the Hollow Ruin is placed: at least, at most. */
	public static final int RUIN_MIN_DISTANCE_BLOCKS = 96;
	public static final int RUIN_MAX_DISTANCE_BLOCKS = 192;
	/** Raiders guarding the Hollow Ruin. */
	public static final int RUIN_GUARDS = 2;

	/**
	 * Players farther than this cannot see a body appear, whatever lies between. Used where survivors and
	 * raiders are placed. A player-sized body is drawn to about 64 blocks at the default entity distance;
	 * this allows for twice that. PROPOSED.
	 */
	public static final int SIGHT_LIMIT_BLOCKS = 128;

	/** Blocks from the camp where survivors start walking in, beyond sight. */
	public static final int SURVIVOR_ORIGIN_DISTANCE_BLOCKS = 96;
	/** Day tick on the first night when survivors set out: dusk. */
	public static final int SURVIVORS_SET_OUT_TIME_OF_DAY = 12500;
}
