package io.github.bl4z3es.riseofempires.core.sim.camp;

import io.github.bl4z3es.riseofempires.core.Wire;
import java.util.List;

/**
 * One piece of work a person should do next, from {@link Jobs#nextOrder}. Both tiers carry it
 * out the same way: {@link Jobs#begin} when the person starts (inputs leave the stockpile),
 * then {@link Jobs#complete} when they finish (outputs arrive). In the Ledger tier the gap is
 * exactly {@code hours}; in the embodied tier it is however long the body really took.
 *
 * @param job    the profession doing it
 * @param step   {@code plant}, {@code harvest}, {@code gather}, {@code build} or {@link #IDLE}
 * @param hours  in-game hours the work takes at full strength
 * @param takes  items taken from the stockpile at {@link Jobs#begin}
 * @param gives  items delivered at {@link Jobs#complete}, before the output multiplier
 * @param siteId the build site for {@code build}; "" otherwise
 * @param reason for {@link #IDLE}: why the person has nothing to do, in plain words
 */
public record WorkOrder(String job, String step, int hours, List<ItemCount> takes, List<ItemCount> gives,
                        String siteId, String reason) {
    public static final String IDLE = "idle";

    public WorkOrder {
        takes = List.copyOf(takes);
        gives = List.copyOf(gives);
    }

    public static WorkOrder idle(String job, String reason) {
        return new WorkOrder(job, IDLE, 0, List.of(), List.of(), "", reason);
    }

    public boolean isIdle() {
        return step.equals(IDLE);
    }

    public String encode() {
        return Wire.line(job, step, hours, Wire.list(ItemCount.encodeAll(takes)),
                Wire.list(ItemCount.encodeAll(gives)), siteId, reason);
    }

    public static WorkOrder decode(String s) {
        String[] f = Wire.fields(s);
        return new WorkOrder(f[0], f[1], Integer.parseInt(f[2]), ItemCount.decodeAll(Wire.unlist(f[3])),
                ItemCount.decodeAll(Wire.unlist(f[4])), f[5], f[6]);
    }
}
