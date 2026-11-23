package io.github.bl4z3es.riseofempires.core.sim;

import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.model.SimState;

/**
 * Standing: the one score that threats scale to (CONTEXT.md 2). The first version uses population
 * and territory only; army, research and renown arrive with their systems.
 */
public final class Standing {
	private Standing() {}

	// PROPOSED: CONTEXT.md 2 Standing weights; not in Balance yet
	private static final double POPULATION_WEIGHT = 0.30;
	// PROPOSED: CONTEXT.md 2 Standing weights; not in Balance yet
	private static final double TERRITORY_WEIGHT = 0.25;

	public record Parts(int population, int territoryChunks, double populationTerm, double territoryTerm, double total) {}

	public static Parts of(SimState s, long settlementId) {
		int population = s.residents(settlementId).size();
		int chunks = s.settlement(settlementId).map(Settlement::territoryChunks).orElse(0);
		double popTerm = POPULATION_WEIGHT * log2(Math.max(1, population));
		double terTerm = TERRITORY_WEIGHT * log2(Math.max(1, chunks));
		return new Parts(population, chunks, popTerm, terTerm, popTerm + terTerm);
	}

	private static double log2(double x) { return Math.log(x) / Math.log(2); }
}
