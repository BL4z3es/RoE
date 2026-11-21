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
}
