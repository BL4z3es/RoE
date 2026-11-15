package io.github.bl4z3es.riseofempires.core.sim.camp;

import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.sim.Effects;
import io.github.bl4z3es.riseofempires.core.sim.Simulation;
import java.util.List;

/** Deaths and departures. Every system that ends a life or sends someone away uses these. */
public final class People {
    private People() {}

    public static final String DEATH = "death";
    public static final String LEFT = "left_settlement";

    /** A ref to the settlement a character lives in, or null. */
    public static Ref home(Simulation sim, String characterId) {
        Character c = sim.state().character(characterId);
        return c == null || c.settlementId().isEmpty() ? null : Ref.settlement(c.settlementId());
    }

    /**
     * Records a death. {@code others} are further actors (for example the attacker). The
     * settlement is always listed as an actor so its history can be read back.
     */
    public static Event die(Simulation sim, String characterId, String summary, List<Ref> others, long... causes) {
        return end(sim, DEATH, Character.Status.DEAD, characterId, summary, others, causes);
    }

    public static Event leave(Simulation sim, String characterId, String summary, long... causes) {
        return end(sim, LEFT, Character.Status.LEFT, characterId, summary, List.of(), causes);
    }

    private static Event end(Simulation sim, String type, Character.Status status, String characterId,
                             String summary, List<Ref> others, long... causes) {
        Character c = sim.state().character(characterId);
        if (c == null || !c.alive()) return null;
        EventDraft d = EventDraft.of(type).actor(Ref.character(characterId)).summary(summary)
                .effect(Effects.setStatus(characterId, status));
        Settlement home = sim.state().settlement(c.settlementId());
        if (home != null) {
            d.actor(Ref.settlement(home.id())).at(home.centre())
                    .effect(Effects.removeResident(home.id(), characterId));
            for (Character other : sim.state().residents(home.id())) {
                if (!other.id().equals(characterId)) d.witness(Ref.character(other.id()));
            }
        }
        for (Ref r : others) d.actor(r);
        for (long cause : causes) d.cause(cause);
        if (status == Character.Status.LEFT && home != null) {
            d.effect(Effects.remember(characterId, "Left " + home.name() + "."));
        }
        return sim.emit(d);
    }
}
