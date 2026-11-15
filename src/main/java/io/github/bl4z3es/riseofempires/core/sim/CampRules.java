package io.github.bl4z3es.riseofempires.core.sim;

import io.github.bl4z3es.riseofempires.core.sim.camp.ActOne;
import io.github.bl4z3es.riseofempires.core.sim.camp.Jobs;
import io.github.bl4z3es.riseofempires.core.sim.camp.Needs;
import io.github.bl4z3es.riseofempires.core.sim.camp.Raids;
import io.github.bl4z3es.riseofempires.core.sim.camp.Survivors;

/**
 * Every rule of Act I, in the order they run each hour. The Minecraft layer calls this once
 * when it creates the simulation. Look systems up afterwards with {@code sim.system(Jobs.ID)}.
 */
public final class CampRules {
    private CampRules() {}

    public static Simulation install(Simulation sim) {
        return sim.add(new CalendarSystem())
                .add(new Survivors())
                .add(new Jobs())
                .add(new Needs())
                .add(new Raids())
                .add(new ActOne());
    }

    public static Jobs jobs(Simulation sim) { return (Jobs) sim.system(Jobs.ID); }
    public static Survivors survivors(Simulation sim) { return (Survivors) sim.system(Survivors.ID); }
    public static Raids raids(Simulation sim) { return (Raids) sim.system(Raids.ID); }
}
