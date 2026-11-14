package io.github.bl4z3es.riseofempires.core.sim;

/**
 * One set of rules. A system reads the world and emits events; it never writes state itself.
 * Systems run once per in-game hour, in the order they were added to the {@link Simulation}.
 */
public interface SimSystem {
    /** A short unique name, also used to seed this system's random stream. */
    String id();

    /** Called once when the system is added. Register custom effect kinds here. */
    default void install(Simulation sim) {}

    /** Called once per in-game hour, after the clock has moved to {@code sim.state().hour()}. */
    void hourly(Simulation sim);
}
