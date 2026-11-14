package io.github.bl4z3es.riseofempires.core.sim;

import io.github.bl4z3es.riseofempires.core.Rng;
import io.github.bl4z3es.riseofempires.core.ledger.Effect;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.Ledger;
import java.util.ArrayList;
import java.util.List;

/**
 * The world, its history and its rules in one place. {@link #emit} is the single door through
 * which state changes: it writes the event to the Ledger and applies its effects (rule E2).
 * Both rule systems in core and the mc layer use it.
 */
public final class Simulation {
    private final WorldState state;
    private final Ledger ledger;
    private final EffectApplier applier = new EffectApplier();
    private final List<SimSystem> systems = new ArrayList<>();

    public Simulation(WorldState state, Ledger ledger) {
        this.state = state;
        this.ledger = ledger;
    }

    public WorldState state() { return state; }
    public Ledger ledger() { return ledger; }
    public EffectApplier applier() { return applier; }
    public List<SimSystem> systems() { return List.copyOf(systems); }

    public Simulation add(SimSystem system) {
        systems.add(system);
        system.install(this);
        return this;
    }

    /** The system with this id, or null. Lets one system call another's query methods. */
    public SimSystem system(String id) {
        for (SimSystem s : systems) if (s.id().equals(id)) return s;
        return null;
    }

    /** Records the event at the current hour and applies its effects. */
    public Event emit(EventDraft draft) {
        for (Effect effect : draft.effects()) {
            if (!applier.knows(effect.kind())) {
                throw new IllegalArgumentException("No handler for effect kind " + effect.kind());
            }
        }
        Event event = ledger.append(draft, state.hour());
        for (Effect effect : event.effects()) applier.apply(state, effect);
        return event;
    }

    /** A random stream for one system in the current hour. Same world, hour and label: same rolls. */
    public Rng rng(String label) {
        return Rng.of(state.seed(), state.hour(), label);
    }

    /** Moves the clock forward one in-game hour and runs every system once. */
    public void advanceHour() {
        state.setHour(state.hour() + 1);
        for (SimSystem system : systems) system.hourly(this);
    }

    /** Runs whole hours until the clock reads {@code hour}. Does nothing if it is already there. */
    public void advanceTo(long hour) {
        while (state.hour() < hour) advanceHour();
    }
}
