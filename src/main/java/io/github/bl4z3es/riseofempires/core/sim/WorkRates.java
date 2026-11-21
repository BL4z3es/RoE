package io.github.bl4z3es.riseofempires.core.sim;

import io.github.bl4z3es.riseofempires.core.Balance;

/**
 * The Ledger tier's expected work rates, built from the same {@link Balance} timings the embodied
 * workers use, so both tiers agree (rule E3). All results are ticks of one well worker's time.
 */
public final class WorkRates {
	private WorkRates() {}

	/** Ticks a farmer spends per plot: harvest, replant, step to the next plot, and a share of the trip to the store. */
	public static double farmerTicksPerPlot(double blocksToStore, double itemsPerPlot) {
		return Balance.HARVEST_TICKS + Balance.REPLANT_TICKS + Balance.WALK_TICKS_PER_BLOCK
				+ tripTicks(blocksToStore, Balance.DEPOSIT_TICKS) * itemsPerPlot / Balance.CARRY_CAPACITY;
	}

	/** Ticks a gatherer spends per item: half logs and half stone, the walk between finds, and a share of the trip. */
	public static double gathererTicksPerItem(double blocksToStore) {
		return (Balance.CHOP_TICKS + Balance.MINE_TICKS) / 2.0
				+ Balance.GATHER_AVERAGE_WALK_BLOCKS * Balance.WALK_TICKS_PER_BLOCK
				+ tripTicks(blocksToStore, Balance.DEPOSIT_TICKS) / Balance.CARRY_CAPACITY;
	}

	/** Ticks a builder spends per block: place it, step along, and a share of the trip to take materials. */
	public static double builderTicksPerBlock(double blocksToStore) {
		return Balance.PLACE_BLOCK_TICKS + Balance.WALK_TICKS_PER_BLOCK
				+ tripTicks(blocksToStore, Balance.TAKE_TICKS) / Balance.CARRY_CAPACITY;
	}

	private static double tripTicks(double blocksToStore, int handlingTicks) {
		return 2 * blocksToStore * Balance.WALK_TICKS_PER_BLOCK + handlingTicks;
	}
}
