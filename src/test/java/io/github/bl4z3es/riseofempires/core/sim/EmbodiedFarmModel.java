package io.github.bl4z3es.riseofempires.core.sim;

import io.github.bl4z3es.riseofempires.core.Balance;
import java.util.List;
import java.util.SplittableRandom;

/**
 * A tick-by-tick model of what the embodied (in-world) tier does with one farmer: crops take
 * random ticks, the farmer walks to the nearest mature plot, harvests and replants it, carries the
 * crop and spare seeds, and walks them to the granary. Used only by tests to check the Ledger tier
 * against it (rule E3).
 */
final class EmbodiedFarmModel {
	/** Per-tick chance that a random tick lands on a block and advances the crop, before the season. */
	private static final double VANILLA_STAGE_CHANCE_PER_TICK = (3.0 / 4096.0) * (1.0 / 6.0);

	record Pos(int x, int z) {
		int manhattan(Pos o) { return Math.abs(x - o.x) + Math.abs(z - o.z); }
	}

	private enum Phase { IDLE, WALK_TO_PLOT, WORK, WALK_TO_STORE, DEPOSIT }

	private final List<Pos> plots;
	private final Pos granary;
	private final double output;

	private long wheatDeposited;
	private long idleTicks;

	EmbodiedFarmModel(List<Pos> plots, Pos granary, double outputMultiplier) {
		this.plots = List.copyOf(plots);
		this.granary = granary;
		this.output = outputMultiplier;
	}

	long wheatDeposited() { return wheatDeposited; }
	long idleTicks() { return idleTicks; }

	/** Runs whole days from day 0 and returns the wheat deposited in the granary. */
	long runDays(int days, long seed) { return runDays(0, days, seed); }

	/** Runs whole days from the given start day (so a test can start in winter). */
	long runDays(long startDay, int days, long seed) {
		SplittableRandom growth = Dice.of(seed, 1);
		SplittableRandom drops = Dice.of(seed, 2);
		int[] age = new int[plots.size()];
		wheatDeposited = 0;
		idleTicks = 0;

		Pos at = granary;
		Phase phase = Phase.IDLE;
		int remaining = 0;
		int target = -1;
		int carriedWheat = 0, carriedSeeds = 0;
		long start = startDay * GameTime.TICKS_PER_DAY;
		long total = start + days * GameTime.TICKS_PER_DAY;
		double multiplier = 0;

		for (long tick = start; tick < total; tick++) {
			if (tick % GameTime.TICKS_PER_HOUR == 0) {
				multiplier = Farming.growthMultiplier(Calendar.season(tick));
			}
			double chance = VANILLA_STAGE_CHANCE_PER_TICK * multiplier;
			for (int i = 0; i < age.length; i++) {
				if (age[i] < Farming.WHEAT_MAX_AGE && growth.nextDouble() < chance) age[i]++;
			}

			// Pick the next job when free.
			if (phase == Phase.IDLE) {
				int nearest = nearestMature(age, at);
				int carried = carriedWheat + carriedSeeds;
				if (nearest >= 0 && carried < Balance.CARRY_CAPACITY) {
					target = nearest;
					remaining = adjusted(at.manhattan(plots.get(target)) * Balance.WALK_TICKS_PER_BLOCK, false);
					phase = Phase.WALK_TO_PLOT;
				} else if (carried > 0) {
					remaining = at.manhattan(granary) * Balance.WALK_TICKS_PER_BLOCK;
					phase = Phase.WALK_TO_STORE;
				} else {
					idleTicks++;
				}
			}

			// Spend this tick on the current job.
			switch (phase) {
				case IDLE -> { }
				case WALK_TO_PLOT -> {
					if (--remaining <= 0) {
						at = plots.get(target);
						remaining = adjusted(Balance.HARVEST_TICKS + Balance.REPLANT_TICKS, true);
						phase = Phase.WORK;
					}
				}
				case WORK -> {
					if (--remaining <= 0) {
						if (age[target] >= Farming.WHEAT_MAX_AGE) {
							int seeds = Farming.seedsFromHarvest(drops);
							age[target] = 0;
							carriedWheat++;
							carriedSeeds += seeds - 1; // one seed is replanted
						}
						// Replanting is part of the work time; plots stay one step apart.
						phase = Phase.IDLE;
					}
				}
				case WALK_TO_STORE -> {
					if (--remaining <= 0) {
						at = granary;
						remaining = adjusted(Balance.DEPOSIT_TICKS, true);
						phase = Phase.DEPOSIT;
					}
				}
				case DEPOSIT -> {
					if (--remaining <= 0) {
						wheatDeposited += carriedWheat;
						carriedWheat = 0;
						carriedSeeds = 0;
						phase = Phase.IDLE;
					}
				}
			}
		}
		return wheatDeposited;
	}

	private int adjusted(int ticks, boolean scaleWithOutput) {
		if (!scaleWithOutput || output <= 0) return ticks;
		return (int) Math.ceil(ticks / output);
	}

	private int nearestMature(int[] age, Pos from) {
		int best = -1, bestDist = Integer.MAX_VALUE;
		for (int i = 0; i < age.length; i++) {
			if (age[i] < Farming.WHEAT_MAX_AGE) continue;
			int d = from.manhattan(plots.get(i));
			if (d < bestDist) { best = i; bestDist = d; }
		}
		return best;
	}
}
