package io.github.bl4z3es.riseofempires.core.model;

/** Unmet needs, counted. Both counters fall back to zero when the need is met. */
public record Needs(int missedMeals, int nightsRough) {
	public static final Needs MET = new Needs(0, 0);
}
