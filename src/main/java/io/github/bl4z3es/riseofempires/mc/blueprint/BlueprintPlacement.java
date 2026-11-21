package io.github.bl4z3es.riseofempires.mc.blueprint;

import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.model.Blueprint;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import io.github.bl4z3es.riseofempires.core.sim.Building;
import io.github.bl4z3es.riseofempires.core.sim.Founding;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** The rule for putting a blueprint down: it must go inside the camp the player founded. */
public final class BlueprintPlacement {
	private BlueprintPlacement() {}

	/** What happened when a blueprint was used on a block. */
	public enum Outcome { PLACED, OUTSIDE_CAMP, UNKNOWN_BLUEPRINT }

	/**
	 * The result of an attempt.
	 *
	 * @param event the {@code blueprint_placed} event, present only when {@link Outcome#PLACED}
	 */
	public record Result(Outcome outcome, Optional<Event> event) {}

	/**
	 * Places the blueprint with its origin at the clicked ground block, if that block is inside a camp
	 * the player founded. Writes the Ledger event through {@link Building}. Nothing changes otherwise.
	 *
	 * @param rotation quarter turns clockwise, 0 to 3
	 */
	public static Result place(RoeServer roe, ServerLevel level, BlockPos clicked, String playerUuid, String blueprintId, int rotation) {
		SimState state = roe.state();
		Blueprint blueprint = roe.content().blueprints().get(blueprintId);
		if (blueprint == null) {
			return new Result(Outcome.UNKNOWN_BLUEPRINT, Optional.empty());
		}
		Place origin = RoeServer.place(level, clicked);
		Optional<Long> founded = state.settlementFoundedBy(playerUuid);
		Optional<Settlement> here = Founding.settlementAt(state, origin);
		if (founded.isEmpty() || here.isEmpty() || here.get().id() != founded.get()) {
			return new Result(Outcome.OUTSIDE_CAMP, Optional.empty());
		}
		Event e = Building.placeBlueprint(state, founded.get(), blueprint, origin, Math.floorMod(rotation, 4), playerUuid, roe.now());
		return new Result(Outcome.PLACED, Optional.of(e));
	}
}
