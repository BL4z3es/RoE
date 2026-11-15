package io.github.bl4z3es.riseofempires.core.sim.camp;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.Rng;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.sim.Calendar;
import io.github.bl4z3es.riseofempires.core.sim.Effects;
import io.github.bl4z3es.riseofempires.core.sim.Founding;
import io.github.bl4z3es.riseofempires.core.sim.SimSystem;
import io.github.bl4z3es.riseofempires.core.sim.Simulation;
import io.github.bl4z3es.riseofempires.core.sim.WorldState;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The first survivors (task M1-11). At the first nightfall after a settlement is founded,
 * {@link Balance#STARTING_SURVIVORS} people set out for it. They exist as characters from that
 * moment but belong to no settlement until admitted.
 *
 * <p>Ledger tier: they are admitted when they arrive. Embodied tier: the Minecraft layer walks
 * them in from beyond sight and calls {@link #admit} or {@link #turnAway} when the player
 * answers them. Until then they wait.
 */
public final class Survivors implements SimSystem {
    public static final String ID = "survivors";

    public static final String SET_OUT = "survivors_set_out";
    public static final String JOINED = "survivor_joined";
    public static final String TURNED_AWAY = "survivor_turned_away";

    /** Character tag: the settlement a survivor is walking to. Cleared on admit or turn away. */
    public static final String BOUND_FOR = "survivor.bound_for";
    /** Character stat: the hour a survivor reaches the camp. */
    public static final String ARRIVE_HOUR = "survivor.arrive_hour";
    /** Character stat: id of the {@link #SET_OUT} event that created them. */
    public static final String SET_OUT_EVENT = "survivor.set_out_event";
    /** Settlement tag: "1" once survivors have been sent. */
    public static final String SENT = "survivors.sent";
    /** Settlement stat: 1 once anyone has lived there. A settlement that had people and has none is lost. */
    public static final String EVER_PEOPLED = "ever_peopled";

    @Override
    public String id() { return ID; }

    @Override
    public void hourly(Simulation sim) {
        WorldState state = sim.state();
        long hour = state.hour();
        if (Calendar.hourOfDay(hour) == Balance.NIGHT_START_HOUR) {
            for (Settlement s : List.copyOf(state.settlements())) {
                if (!state.tag(Ref.settlement(s.id()), SENT).isEmpty()) continue;
                if (state.nation(s.nationId()) == null || !state.nation(s.nationId()).playerRuled()) continue;
                if (state.stat(Ref.settlement(s.id()), Founding.FOUNDED_HOUR) >= hour) continue;
                sendSurvivors(sim, s);
            }
        }
        for (Character c : List.copyOf(state.characters())) {
            String bound = state.tag(Ref.character(c.id()), BOUND_FOR);
            if (bound.isEmpty() || !c.alive()) continue;
            if (state.embodied(bound)) continue;
            if (hour >= state.stat(Ref.character(c.id()), ARRIVE_HOUR)) admit(sim, bound, c.id());
        }
    }

    private void sendSurvivors(Simulation sim, Settlement s) {
        Rng rng = sim.rng(ID + ":" + s.id());
        Set<String> usedNames = new HashSet<>();
        for (Character c : sim.state().characters()) usedNames.add(c.name());
        List<String> names = new ArrayList<>(SurvivorTables.NAMES);
        names.removeAll(usedNames);
        // Names repeat once the pool runs low: a big world has more than one Mara.
        if (names.size() < Balance.STARTING_SURVIVORS) names = new ArrayList<>(SurvivorTables.NAMES);
        List<SurvivorTables.Trait> traits = new ArrayList<>(SurvivorTables.TRAITS);
        List<String> wants = new ArrayList<>(SurvivorTables.WANTS);
        List<String> memories = new ArrayList<>(SurvivorTables.MEMORIES);

        long eventId = sim.ledger().size() + 1L;
        long arrive = sim.state().hour() + Balance.SURVIVOR_WALK_HOURS;
        EventDraft d = EventDraft.of(SET_OUT).at(s.centre()).actor(Ref.settlement(s.id()))
                .cause(Founding.foundingEvent(sim.ledger(), s.id()))
                .effect(Effects.setTag(Ref.settlement(s.id()), SENT, "1"));
        List<String> who = new ArrayList<>();
        for (int i = 0; i < Balance.STARTING_SURVIVORS; i++) {
            String name = names.remove(rng.nextInt(names.size()));
            SurvivorTables.Trait trait = traits.remove(rng.nextInt(traits.size()));
            String want = wants.remove(rng.nextInt(wants.size()));
            String memory = memories.remove(rng.nextInt(memories.size()));
            String id = "character-" + eventId + "-" + (i + 1);
            Character c = new Character(id, name, trait.name(), want, "", List.of(memory), "", Character.Status.ALIVE);
            Ref ref = Ref.character(id);
            d.actor(ref)
                    .effect(Effects.putCharacter(c))
                    .effect(Effects.setTag(ref, BOUND_FOR, s.id()))
                    .effect(Effects.setStat(ref, ARRIVE_HOUR, arrive))
                    .effect(Effects.setStat(ref, SET_OUT_EVENT, eventId));
            who.add(name);
        }
        d.summary(String.join(", ", who.subList(0, who.size() - 1)) + " and " + who.get(who.size() - 1)
                + " saw the fire at " + s.name() + " and set out towards it in the dark.");
        sim.emit(d);
    }

    /** Survivors still on their way to, or waiting outside, this settlement. */
    public static List<Character> waiting(WorldState state, String settlementId) {
        List<Character> out = new ArrayList<>();
        for (Character c : state.characters()) {
            if (c.alive() && state.tag(Ref.character(c.id()), BOUND_FOR).equals(settlementId)) out.add(c);
        }
        return out;
    }

    /**
     * A survivor has reached the camp and been allowed to stay. Adds them as a resident and puts
     * what they carried into the stockpile. Returns null if they are not bound for this settlement.
     */
    public Event admit(Simulation sim, String settlementId, String characterId) {
        WorldState state = sim.state();
        Character c = state.character(characterId);
        Settlement s = state.settlement(settlementId);
        Ref ref = Ref.character(characterId);
        if (c == null || s == null || !c.alive() || !state.tag(ref, BOUND_FOR).equals(settlementId)) return null;
        return sim.emit(EventDraft.of(JOINED).at(s.centre()).actor(ref).actor(Ref.settlement(settlementId))
                .cause((long) state.stat(ref, SET_OUT_EVENT))
                .effect(Effects.addResident(settlementId, characterId))
                .effect(Effects.setTag(ref, BOUND_FOR, ""))
                .effect(Effects.addStock(s.stockpileId(), Food.BREAD, Balance.SURVIVOR_BREAD))
                .effect(Effects.addStock(s.stockpileId(), Food.SEEDS, Balance.SURVIVOR_SEEDS))
                .effect(Effects.setStat(Ref.settlement(settlementId), EVER_PEOPLED, 1))
                .effect(Effects.remember(characterId, "Was taken in at " + s.name() + "."))
                .summary(c.name() + " (" + c.trait() + ", wants " + c.want() + ") joined " + s.name()
                        + ", bringing " + Balance.SURVIVOR_BREAD + " bread and " + Balance.SURVIVOR_SEEDS + " seeds."));
    }

    /** The player sent a survivor away. Nothing stops this (law L4); they remember it. */
    public Event turnAway(Simulation sim, String settlementId, String characterId) {
        WorldState state = sim.state();
        Character c = state.character(characterId);
        Settlement s = state.settlement(settlementId);
        Ref ref = Ref.character(characterId);
        if (c == null || s == null || !c.alive() || !state.tag(ref, BOUND_FOR).equals(settlementId)) return null;
        return sim.emit(EventDraft.of(TURNED_AWAY).at(s.centre()).actor(ref).actor(Ref.settlement(settlementId))
                .cause((long) state.stat(ref, SET_OUT_EVENT))
                .effect(Effects.setTag(ref, BOUND_FOR, ""))
                .effect(Effects.setStatus(characterId, Character.Status.LEFT))
                .effect(Effects.remember(characterId, "Was turned away from " + s.name() + " in the night."))
                .summary(c.name() + " was turned away from " + s.name() + " and walked back into the dark."));
    }
}
