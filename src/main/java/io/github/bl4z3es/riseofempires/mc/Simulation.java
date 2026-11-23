package io.github.bl4z3es.riseofempires.mc;

import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import io.github.bl4z3es.riseofempires.core.sim.ActOne;
import io.github.bl4z3es.riseofempires.core.sim.Building;
import io.github.bl4z3es.riseofempires.core.sim.LedgerTier;
import io.github.bl4z3es.riseofempires.core.sim.NeedsRules;
import io.github.bl4z3es.riseofempires.core.sim.Raids;
import io.github.bl4z3es.riseofempires.core.sim.Seasons;
import io.github.bl4z3es.riseofempires.mc.world.DeferredEdits;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Advances the core rules to the current world time, in a fixed order. The same rules run for every
 * settlement; the only difference between tiers is who does the work: bodies in the world when the
 * area is loaded, {@link LedgerTier} when it is not (rules E1 and E3).
 */
final class Simulation {
	private static List<Event> last = List.of();

	private Simulation() {}

	static List<Event> lastEvents() { return last; }

	static void advance(RoeServer roe, long now, Set<Long> sleptTonight) {
		SimState s = roe.state();
		List<Event> out = new ArrayList<>();
		out.addAll(Seasons.update(s, now));

		for (Settlement settlement : List.copyOf(s.settlements())) {
			if (roe.isEmbodied(settlement.centre())) {
				// Bodies do the work; keep the Ledger tier's clock level so it does not count this time twice.
				s.setCursor("ledger." + settlement.id(), Math.floorDiv(now, 1000L) * 1000L);
			} else {
				LedgerTier.Result r = LedgerTier.advance(s, settlement.id(), now, roe.content().blueprints());
				out.addAll(r.events());
				DeferredEdits.queueBuilt(roe, r.built());
				if (!r.built().isEmpty()) {
					Event cause = r.events().get(r.events().size() - 1);
					for (long siteId : r.built().stream().map(LedgerTier.BuiltBlock::siteId).distinct().toList()) {
						Building.completeIfDone(s, siteId, cause, now, roe.content().blueprints()).ifPresent(out::add);
					}
				}
			}
		}

		long nightBefore = s.cursor("needs.night", Long.MIN_VALUE);
		out.addAll(NeedsRules.eatMeals(s, now, roe.content().food()));
		out.addAll(NeedsRules.judgeNights(s, now, Set.copyOf(sleptTonight)));
		if (s.cursor("needs.night", Long.MIN_VALUE) != nightBefore) sleptTonight.clear();

		out.addAll(Raids.update(s, now));
		out.addAll(ActOne.update(s, now, stock -> roe.content().food().totalQuarters(stock)));
		last = List.copyOf(out);
	}
}
