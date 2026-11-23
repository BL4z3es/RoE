package io.github.bl4z3es.riseofempires.core.sim;

import io.github.bl4z3es.riseofempires.core.Balance;

/** Maps world time to days, seasons and years (CONTEXT.md 3.5). Pure functions. */
public final class Calendar {
	private Calendar() {}

	/** Whole days since the world began, from 0. */
	public static long day(long worldTime) { return Math.floorDiv(worldTime, GameTime.TICKS_PER_DAY); }

	/** Seasons since the world began, from 0. Season 0 is the first spring. */
	public static long seasonIndex(long worldTime) { return Math.floorDiv(day(worldTime), Balance.SEASON_LENGTH_DAYS); }

	public static Season season(long worldTime) { return seasonOfIndex(seasonIndex(worldTime)); }

	public static Season seasonOfIndex(long seasonIndex) { return Season.values()[(int) Math.floorMod(seasonIndex, 4L)]; }

	/** Year number for display, from 1. */
	public static long year(long worldTime) { return Math.floorDiv(day(worldTime), Balance.YEAR_LENGTH_DAYS) + 1; }

	/** Day within the season for display, from 1. */
	public static int dayOfSeason(long worldTime) { return (int) Math.floorMod(day(worldTime), (long) Balance.SEASON_LENGTH_DAYS) + 1; }

	/** World time at which a season index begins. */
	public static long seasonStart(long seasonIndex) { return seasonIndex * Balance.SEASON_LENGTH_DAYS * GameTime.TICKS_PER_DAY; }

	/** World time at which a day begins. */
	public static long dayStart(long day) { return day * GameTime.TICKS_PER_DAY; }
}
