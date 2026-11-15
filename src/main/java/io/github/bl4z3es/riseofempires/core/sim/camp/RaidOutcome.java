package io.github.bl4z3es.riseofempires.core.sim.camp;

import java.util.List;

/**
 * How a raid ended. In the Ledger tier {@link Raids} works it out; in the embodied tier the
 * Minecraft layer reports what really happened.
 *
 * @param repelled     true if the raiders were driven off before reaching the food store
 * @param raidersSlain raiders killed
 * @param foodStolen   items carried off (only when not repelled)
 * @param killed       ids of residents killed
 */
public record RaidOutcome(boolean repelled, int raidersSlain, List<ItemCount> foodStolen, List<String> killed) {
    public RaidOutcome {
        foodStolen = List.copyOf(foodStolen);
        killed = List.copyOf(killed);
    }
}
