package io.github.bl4z3es.riseofempires.mc.raid;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import io.github.bl4z3es.riseofempires.mc.world.WorldBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The weak raiders who guard the Hollow Ruin (task M1-09, guards). They are placed once, from this
 * module's tick and never from a chunk event, while no player is within the Embodied radius of the ruin
 * and its area is loaded (rule E6: nothing is loaded for them).
 */
public final class RuinGuards implements RoeServer.Module {
	/** Event type written when the guards are placed. */
	public static final String EVENT_TYPE = "ruin_guarded";
	/** PROPOSED: not in Balance yet. How far from the ruin's centre the guards first stand. */
	private static final int STAND_OFFSET = 9;

	@Override
	public void tick(RoeServer roe, long now) {
		SimState state = roe.state();
		if (state.fact("ruin.built").isEmpty() || state.fact("ruin.guarded").isPresent()) return;
		String place = state.fact("ruin.place").orElse(null);
		String y = state.fact("ruin.y").orElse(null);
		String cause = state.fact("ruin.event").orElse(null);
		if (place == null || y == null || cause == null) return;
		String[] xz = place.split(",");
		BlockPos ruin = new BlockPos(Integer.parseInt(xz[0]), Integer.parseInt(y), Integer.parseInt(xz[1]));
		ServerLevel level = roe.overworld();
		if (!level.isPositionEntityTicking(ruin) || WorldBlocks.playerNear(level, ruin)) return;

		int placed = 0;
		for (int i = 0; i < Balance.RUIN_GUARDS; i++) {
			int x = ruin.getX() + (i % 2 == 0 ? STAND_OFFSET : -STAND_OFFSET);
			int z = ruin.getZ() + (i / 2) * 2;
			BlockPos p = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
			RaiderEntity r = RaiderEntity.spawn(level, 0, p);
			if (r == null) continue;
			r.giveOrder(RaiderEntity.Order.GUARD, ruin);
			placed++;
		}
		if (placed == 0) return;
		long causeId = Long.parseLong(cause);
		Event e = state.ledger().append(EventDraft.of(EVENT_TYPE, now).at(RoeServer.place(level, ruin))
				.actor(Ref.world()).effect(Ref.world(), "guards", Integer.toString(placed)).cause(causeId));
		state.putFact("ruin.guarded", Integer.toString(placed), e);
	}
}
