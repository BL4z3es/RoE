package io.github.bl4z3es.riseofempires.core.sim.camp;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.sim.WorldState;

/**
 * Standing, first version (task M1-19): population and territory only. The army, research and
 * renown terms from CONTEXT.md section 2 arrive with the systems that produce them.
 *
 * @param population     living residents across the nation's settlements
 * @param territoryChunks claimed area in chunks
 */
public record Standing(double score, double populationPart, double territoryPart, int population,
                       double territoryChunks) {

    public static Standing of(WorldState state, String nationId) {
        int population = 0;
        double area = 0;
        for (Settlement s : state.settlements()) {
            if (!s.nationId().equals(nationId)) continue;
            population += state.residents(s.id()).size();
            area += Math.PI * s.claimRadius() * s.claimRadius();
        }
        double chunks = area / Balance.BLOCKS_PER_CHUNK;
        double pop = Balance.STANDING_POPULATION_WEIGHT * log2(Math.max(1, population));
        double land = Balance.STANDING_TERRITORY_WEIGHT * log2(Math.max(1, chunks));
        return new Standing(pop + land, pop, land, population, chunks);
    }

    private static double log2(double x) {
        return Math.log(x) / Math.log(2);
    }
}
