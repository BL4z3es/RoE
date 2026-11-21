package io.github.bl4z3es.riseofempires.core.sim;

/** Minecraft's clock as plain numbers. These are facts of the game, not balance values. */
public final class GameTime {
	private GameTime() {}

	public static final long TICKS_PER_DAY = 24_000;
	public static final long TICKS_PER_HOUR = 1_000;

	/** Ticks into the current day, 0 at sunrise. */
	public static long timeOfDay(long worldTime) { return Math.floorMod(worldTime, TICKS_PER_DAY); }
}
