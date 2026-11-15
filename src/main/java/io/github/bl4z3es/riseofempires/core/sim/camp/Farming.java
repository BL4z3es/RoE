package io.github.bl4z3es.riseofempires.core.sim.camp;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.sim.Calendar;

/** Seasonal growth (task M1-08). The Minecraft layer uses the same function for real crop blocks. */
public final class Farming {
    private Farming() {}

    /** How fast crops grow: none outdoors in winter, faster in spring, normal otherwise. */
    public static double growthMultiplier(Calendar.Season season, boolean outdoors) {
        if (season == Calendar.Season.WINTER && outdoors) return 0;
        if (season == Calendar.Season.SPRING) return Balance.SPRING_GROWTH_MULTIPLIER;
        return 1.0;
    }

    /** Hours of normal growing weather a crop planted at {@code plantedHour} has had by {@code now}. */
    public static double growth(long plantedHour, long now, boolean outdoors) {
        double total = 0;
        for (long h = plantedHour; h < now; h++) total += growthMultiplier(Calendar.season(h), outdoors);
        return total;
    }

    public static boolean ripe(long plantedHour, long now, boolean outdoors) {
        return growth(plantedHour, now, outdoors) >= Balance.CROP_GROWTH_HOURS;
    }
}
