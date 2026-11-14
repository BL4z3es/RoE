package io.github.bl4z3es.riseofempires.core;

import java.util.List;
import java.util.SplittableRandom;

/**
 * The only random source allowed in core (rule E4). It is stateless between hours: a system asks
 * for {@code Rng.of(worldSeed, hour, "its-own-label")} and always gets the same stream for the
 * same inputs, so nothing about randomness has to be saved.
 */
public final class Rng {
    private final SplittableRandom random;

    private Rng(long seed) {
        this.random = new SplittableRandom(seed);
    }

    public static Rng of(long worldSeed, long hour, String label) {
        long h = worldSeed * 0x9E3779B97F4A7C15L + hour;
        h = (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L + label.hashCode();
        return new Rng(h ^ (h >>> 29));
    }

    /** 0 inclusive to bound exclusive. */
    public int nextInt(int bound) {
        return random.nextInt(bound);
    }

    /** min and max both inclusive. */
    public int between(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    public double nextDouble() {
        return random.nextDouble();
    }

    public boolean chance(double probability) {
        return random.nextDouble() < probability;
    }

    public <T> T pick(List<T> items) {
        return items.get(random.nextInt(items.size()));
    }
}
