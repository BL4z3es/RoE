package io.github.bl4z3es.riseofempires.core.sim;

import io.github.bl4z3es.riseofempires.core.Balance;
import java.util.SplittableRandom;

/** Crop growth rules shared by every simulation tier. Pure functions. */
public final class Farming {
	private Farming() {}

	public static final String WHEAT = "minecraft:wheat";
	public static final String WHEAT_SEEDS = "minecraft:wheat_seeds";
	public static final int WHEAT_MAX_AGE = 7;

	/**
	 * Expected ticks for vanilla wheat on a fully hydrated field to advance one stage. A block gets a
	 * random tick with chance 3/4096 per tick, and a random tick advances the crop with chance 1/6
	 * on a full hydrated field, so the expected wait is 4096 / 3 * 6 = 8192 ticks.
	 */
	public static final double VANILLA_TICKS_PER_STAGE = 8192.0;

	// Vanilla loot table value, a fact of the game and not a balance choice.
	private static final double WHEAT_BONUS_SEED_CHANCE = 0.5714;

	/** Growth speed of the season as a multiple of vanilla. */
	public static double growthMultiplier(Season s) {
		return switch (s) {
			case SPRING -> Balance.SPRING_GROWTH_MULTIPLIER;
			case WINTER -> Balance.WINTER_GROWTH_MULTIPLIER;
			default -> 1.0;
		};
	}

	/** Chance that a crop advances one stage over the given span of ticks in a season. */
	public static double stageChance(long ticks, Season s) {
		return Math.min(1.0, ticks * growthMultiplier(s) / VANILLA_TICKS_PER_STAGE);
	}

	/** Seeds dropped by one harvested wheat: 1 plus a binomial(3, 0.5714), as in vanilla loot. */
	public static int seedsFromHarvest(SplittableRandom r) {
		int seeds = 1;
		for (int i = 0; i < 3; i++) if (r.nextDouble() < WHEAT_BONUS_SEED_CHANCE) seeds++;
		return seeds;
	}
}
