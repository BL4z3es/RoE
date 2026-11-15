package io.github.bl4z3es.riseofempires.mc.entity;

import io.github.bl4z3es.riseofempires.core.sim.camp.WorkOrder;
import net.minecraft.core.BlockPos;

/** What a body is doing right now. Kept on the body only; the order itself lives in the simulation. */
final class BodyTask {
    enum Phase { NONE, TO_WORK, WORKING, TO_STORE, ARRIVING, WAITING, LEAVING }

    Phase phase = Phase.NONE;
    WorkOrder order;
    BlockPos target;
    int ticks;
    int stuck;
    long nextLook;

    void reset() {
        phase = Phase.NONE;
        order = null;
        target = null;
        ticks = 0;
        stuck = 0;
    }
}
