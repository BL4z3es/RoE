package io.github.bl4z3es.riseofempires.core.sim;

import java.util.SplittableRandom;

/**
 * Seeded randomness (rule E4). A roll is fixed by the world seed and the salts that name what is being
 * rolled, such as an event id and a character id, so the same world gives the same result every time.
 */
public final class Dice {
	private Dice() {}

	public static SplittableRandom of(long seed, long... salts) {
		long h = seed ^ 0x9E3779B97F4A7C15L;
		for (long s : salts) {
			h ^= s + 0x9E3779B97F4A7C15L + (h << 6) + (h >>> 2);
			h = mix(h);
		}
		return new SplittableRandom(h);
	}

	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}
}
