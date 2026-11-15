package io.github.bl4z3es.riseofempires.core.sim;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.Ledger;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Nation;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import java.util.List;

/** Founding a settlement with the Charter (task M1-10). */
public final class Founding {
    private Founding() {}

    public static final String SETTLEMENT_FOUNDED = "settlement_founded";
    /** Settlement stat: the hour it was founded. */
    public static final String FOUNDED_HOUR = "founded_hour";

    /** The id of the event that founded this settlement, or 0 if none is recorded. */
    public static long foundingEvent(Ledger ledger, String settlementId) {
        Ref ref = Ref.settlement(settlementId);
        for (Event e : ledger.all()) {
            if (e.type().equals(SETTLEMENT_FOUNDED) && e.actors().contains(ref)) return e.id();
        }
        return 0;
    }

    /** The nation a player rules, or null. */
    public static Nation nationOf(WorldState state, String playerId) {
        for (Nation n : state.nations()) if (n.playerId().equals(playerId)) return n;
        return null;
    }

    /** The settlement whose claim covers this place, or null. The nearest centre wins. */
    public static Settlement settlementAt(WorldState state, Place place) {
        Settlement best = null;
        for (Settlement s : state.settlements()) {
            if (s.claims(place) && (best == null || s.centre().distanceTo(place) < best.centre().distanceTo(place))) {
                best = s;
            }
        }
        return best;
    }

    /**
     * Founds a camp for a player at a place. Creates the player's nation if they have none.
     * Returns null, and changes nothing, if the place is already inside another claim.
     */
    public static Event found(Simulation sim, String playerId, String playerName, Place place) {
        WorldState state = sim.state();
        if (settlementAt(state, place) != null) return null;
        Nation nation = nationOf(state, playerId);
        EventDraft draft = EventDraft.of(SETTLEMENT_FOUNDED).at(place).actor(Ref.player(playerId));
        if (nation == null) {
            nation = new Nation("nation-" + playerId, playerName + "'s Domain", "", playerId, "hollowcrown");
            draft.effect(Effects.putNation(nation));
        }
        String id = "settlement-" + (sim.ledger().size() + 1);
        Settlement camp = new Settlement(id, playerName + "'s Camp", nation.id(), place,
                Balance.STARTING_CLAIM_RADIUS_BLOCKS, List.of(), "stock-" + id, Settlement.RANKS.get(0));
        draft.actor(Ref.settlement(id)).actor(Ref.nation(nation.id()))
                .effect(Effects.putSettlement(camp))
                .effect(Effects.setStat(Ref.settlement(id), FOUNDED_HOUR, state.hour()))
                .summary(playerName + " placed the Charter and founded " + camp.name() + ".");
        return sim.emit(draft);
    }
}
