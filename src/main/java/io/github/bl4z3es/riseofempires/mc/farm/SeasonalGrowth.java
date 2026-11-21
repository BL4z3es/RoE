package io.github.bl4z3es.riseofempires.mc.farm;

import io.github.bl4z3es.riseofempires.core.sim.Calendar;
import io.github.bl4z3es.riseofempires.core.sim.Season;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Which season the world's crops live in (task M1-08). Only the overworld has seasons. */
public final class SeasonalGrowth {
	private SeasonalGrowth() {}

	/** Test-only: when set, every level reports this season. Always null in play. */
	public static Season override;

	/** The season for crops in this level, or null when the mod has no say (not running, or another dimension). */
	public static Season currentSeason(ServerLevel level) {
		if (override != null) return override;
		if (level.dimension() != Level.OVERWORLD) return null;
		return RoeServer.get().map(roe -> Calendar.season(roe.now())).orElse(null);
	}
}
