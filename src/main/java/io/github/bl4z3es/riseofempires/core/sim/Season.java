package io.github.bl4z3es.riseofempires.core.sim;

/** The four seasons, in calendar order. Day 0 of a world is the first day of spring, year 1. */
public enum Season {
	SPRING, SUMMER, AUTUMN, WINTER;

	public Season next() { return values()[(ordinal() + 1) % 4]; }
}
