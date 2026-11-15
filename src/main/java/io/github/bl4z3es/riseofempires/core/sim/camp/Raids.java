package io.github.bl4z3es.riseofempires.core.sim.camp;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.Rng;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Professions;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.model.Stockpile;
import io.github.bl4z3es.riseofempires.core.sim.Calendar;
import io.github.bl4z3es.riseofempires.core.sim.Effects;
import io.github.bl4z3es.riseofempires.core.sim.Founding;
import io.github.bl4z3es.riseofempires.core.sim.SimSystem;
import io.github.bl4z3es.riseofempires.core.sim.Simulation;
import io.github.bl4z3es.riseofempires.core.sim.WorldState;
import java.util.ArrayList;
import java.util.List;

/**
 * The first barbarian raid (task M1-20). One T1 raid per settlement in Act I, from the Wilds,
 * aimed at the food store. Each step is an event caused by the one before:
 * {@link #PLANNED} (hidden) → {@link #ANNOUNCED} → {@link #ARRIVED} → {@link #RESOLVED}.
 *
 * <p>Raiders are not characters: a raid is a count with a direction. When the settlement is not
 * embodied, {@link #calculate} settles the fight. When it is, the Minecraft layer walks the
 * raiders in from beyond sight after {@link #ANNOUNCED}, fights it out, and calls
 * {@link #resolve} with what happened. A watched fight the world never reports is settled by the
 * calculator after {@link Balance#RAID_MAX_BATTLE_HOURS}.
 */
public final class Raids implements SimSystem {
    public static final String ID = "raids";

    public static final String PLANNED = "raid_planned";
    public static final String ANNOUNCED = "raid_announced";
    public static final String ARRIVED = "raid_arrived";
    public static final String RESOLVED = "raid_resolved";

    public static final String KIND = "raid";
    /** Raid tags and stats. */
    public static final String TARGET = "target";
    public static final String DIRECTION = "direction";
    public static final String STATE = "state";
    public static final String SIZE = "size";
    public static final String ANNOUNCE_HOUR = "announce_hour";
    public static final String ARRIVE_HOUR = "arrive_hour";
    public static final String LAST_EVENT = "last_event";
    /** Settlement tag: the raid against it, or "". */
    public static final String CURRENT = "raid.current";

    public static final String STATE_PLANNED = "planned";
    public static final String STATE_ANNOUNCED = "announced";
    public static final String STATE_ARRIVED = "arrived";
    public static final String STATE_RESOLVED = "resolved";

    public static final List<String> DIRECTIONS = List.of("north", "east", "south", "west");

    @Override
    public String id() { return ID; }

    public static Ref ref(String raidId) {
        return new Ref(KIND, raidId);
    }

    /** The raid on this settlement, or "" if none has been planned. */
    public static String raidOn(WorldState state, String settlementId) {
        return state.tag(Ref.settlement(settlementId), CURRENT);
    }

    public static String state(WorldState state, String raidId) {
        return state.tag(ref(raidId), STATE);
    }

    @Override
    public void hourly(Simulation sim) {
        WorldState state = sim.state();
        long hour = state.hour();
        for (Settlement s : List.copyOf(state.settlements())) {
            String raidId = raidOn(state, s.id());
            if (raidId.isEmpty()) {
                maybePlan(sim, s);
                continue;
            }
            Ref r = ref(raidId);
            switch (state(state, raidId)) {
                case STATE_PLANNED -> {
                    if (hour >= state.stat(r, ANNOUNCE_HOUR)) announce(sim, s, raidId);
                }
                case STATE_ANNOUNCED -> {
                    if (hour >= state.stat(r, ARRIVE_HOUR)) arrive(sim, s, raidId);
                }
                case STATE_ARRIVED -> {
                    boolean overdue = hour >= state.stat(r, ARRIVE_HOUR) + Balance.RAID_MAX_BATTLE_HOURS;
                    if (!state.embodied(s.id()) || overdue) resolve(sim, raidId, calculate(sim, raidId));
                }
                default -> { }
            }
        }
    }

    private void maybePlan(Simulation sim, Settlement s) {
        WorldState state = sim.state();
        Ref sref = Ref.settlement(s.id());
        if (state.stat(sref, Survivors.EVER_PEOPLED) == 0 || state.residents(s.id()).isEmpty()) return;
        long founded = (long) state.stat(sref, Founding.FOUNDED_HOUR);
        long hour = state.hour();
        if (Calendar.day(hour) - Calendar.day(founded) < Balance.RAID_FIRST_DAY) return;
        if (Calendar.hourOfDay(hour) != Balance.RAID_PLAN_HOUR) return;

        Standing standing = Standing.of(state, s.nationId());
        int size = (int) Math.round(Balance.T1_RAID_BASE * standing.score());
        size = Math.max(Balance.T1_RAID_MIN, Math.min(Balance.T1_RAID_MAX, size));
        Rng rng = sim.rng(ID + ":" + s.id());
        String direction = rng.pick(DIRECTIONS);
        String raidId = "raid-" + s.id();
        Ref r = ref(raidId);
        long announce = hour + Balance.RAID_ANNOUNCE_DELAY_HOURS;
        EventDraft d = EventDraft.of(PLANNED).at(s.centre()).actor(r).actor(sref)
                .cause(Founding.foundingEvent(sim.ledger(), s.id()))
                .effect(Effects.setTag(r, TARGET, s.id()))
                .effect(Effects.setTag(r, DIRECTION, direction))
                .effect(Effects.setTag(r, STATE, STATE_PLANNED))
                .effect(Effects.setStat(r, SIZE, size))
                .effect(Effects.setStat(r, ANNOUNCE_HOUR, announce))
                .effect(Effects.setStat(r, ARRIVE_HOUR, announce + Balance.RAID_WARNING_HOURS))
                .effect(Effects.setStat(r, LAST_EVENT, sim.ledger().size() + 1L))
                .effect(Effects.setTag(sref, CURRENT, raidId))
                .summary("A war band of " + size + " in the Wilds to the " + direction + " heard of " + s.name()
                        + " (Standing " + String.format(java.util.Locale.ROOT, "%.2f", standing.score())
                        + ") and decided its granary was worth taking.");
        Event joined = sim.ledger().latest(Survivors.JOINED);
        if (joined != null && joined.actors().contains(sref)) d.causedBy(joined);
        sim.emit(d);
    }

    private void announce(Simulation sim, Settlement s, String raidId) {
        WorldState state = sim.state();
        Ref r = ref(raidId);
        List<Character> people = state.residents(s.id());
        String seer = people.isEmpty() ? "Someone" : people.get(0).name();
        long arrive = (long) state.stat(r, ARRIVE_HOUR);
        EventDraft d = EventDraft.of(ANNOUNCED).at(s.centre()).actor(r).actor(Ref.settlement(s.id()))
                .cause((long) state.stat(r, LAST_EVENT))
                .effect(Effects.setTag(r, STATE, STATE_ANNOUNCED))
                .effect(Effects.setStat(r, LAST_EVENT, sim.ledger().size() + 1L))
                .summary(seer + " saw smoke on the horizon to the " + state.tag(r, DIRECTION) + ": raiders, about "
                        + (int) state.stat(r, SIZE) + " of them, will reach " + s.name() + " by day "
                        + (Calendar.day(arrive) + 1) + ", hour " + Calendar.hourOfDay(arrive) + ".");
        for (Character c : people) d.witness(Ref.character(c.id()));
        sim.emit(d);
    }

    private void arrive(Simulation sim, Settlement s, String raidId) {
        WorldState state = sim.state();
        Ref r = ref(raidId);
        sim.emit(EventDraft.of(ARRIVED).at(s.centre()).actor(r).actor(Ref.settlement(s.id()))
                .cause((long) state.stat(r, LAST_EVENT))
                .effect(Effects.setTag(r, STATE, STATE_ARRIVED))
                .effect(Effects.setStat(r, LAST_EVENT, sim.ledger().size() + 1L))
                .summary((int) state.stat(r, SIZE) + " raiders in leather came out of the " + state.tag(r, DIRECTION)
                        + " and made for the granary at " + s.name() + "."));
    }

    /** Defence strength: every living resident counts, fighters more, all scaled by how well they are. */
    public static double defence(WorldState state, String settlementId) {
        double total = 0;
        for (Character c : state.residents(settlementId)) {
            double base = c.profession().equals(Professions.FIGHTER) ? Balance.RAID_FIGHTER_STRENGTH
                    : Balance.RAID_RESIDENT_STRENGTH;
            total += base * Needs.outputMultiplier(state, c.id());
        }
        return total;
    }

    /** The deterministic battle calculator for unwatched raids. */
    public RaidOutcome calculate(Simulation sim, String raidId) {
        WorldState state = sim.state();
        Ref r = ref(raidId);
        Settlement s = state.settlement(state.tag(r, TARGET));
        int size = (int) state.stat(r, SIZE);
        double attack = size * Balance.RAID_RAIDER_STRENGTH;
        double defence = defence(state, s.id());
        if (defence >= attack) {
            int slain = (int) Math.min(size, Math.floor(defence / Balance.RAID_RAIDER_STRENGTH / 2));
            return new RaidOutcome(true, slain, List.of(), List.of());
        }
        int carry = size * Balance.RAID_FOOD_PER_RAIDER;
        Stockpile stock = state.stockpile(s.stockpileId());
        List<ItemCount> stolen = new ArrayList<>();
        for (String item : Food.MEAL_VALUES.keySet()) {
            if (carry <= 0) break;
            int n = Math.min(carry, stock.count(item));
            if (n > 0) { stolen.add(new ItemCount(item, n)); carry -= n; }
        }
        List<String> killed = new ArrayList<>();
        List<Character> people = state.residents(s.id());
        if (attack >= defence * Balance.RAID_KILL_RATIO && !people.isEmpty()) {
            killed.add(sim.rng(ID + ":" + raidId).pick(people).id());
        }
        int slain = (int) Math.floor(defence / Balance.RAID_RAIDER_STRENGTH / 2);
        return new RaidOutcome(false, Math.min(size, slain), stolen, killed);
    }

    /**
     * Ends the raid with this outcome. Food leaves the stockpile and each killed resident gets a
     * death event caused by the raid. Returns null if the raid is not underway.
     */
    public Event resolve(Simulation sim, String raidId, RaidOutcome outcome) {
        WorldState state = sim.state();
        Ref r = ref(raidId);
        String st = state(state, raidId);
        if (!st.equals(STATE_ARRIVED) && !st.equals(STATE_ANNOUNCED)) return null;
        Settlement s = state.settlement(state.tag(r, TARGET));
        Ref sref = Ref.settlement(s.id());
        long id = sim.ledger().size() + 1L;
        EventDraft d = EventDraft.of(RESOLVED).at(s.centre()).actor(r).actor(sref)
                .cause((long) state.stat(r, LAST_EVENT))
                .effect(Effects.setTag(r, STATE, STATE_RESOLVED))
                .effect(Effects.setStat(r, LAST_EVENT, id));
        for (Character c : state.residents(s.id())) d.witness(Ref.character(c.id()));
        String summary;
        if (outcome.repelled()) {
            summary = "The raiders were driven off from " + s.name() + (outcome.raidersSlain() > 0
                    ? ", leaving " + outcome.raidersSlain() + " dead." : ".");
        } else {
            for (ItemCount i : outcome.foodStolen()) d.effect(Effects.addStock(s.stockpileId(), i.item(), -i.count()));
            if (!outcome.foodStolen().isEmpty()) d.effect(Effects.setTag(sref, Needs.LAST_THEFT, String.valueOf(id)));
            summary = "The raiders broke into the granary at " + s.name() + (outcome.foodStolen().isEmpty()
                    ? " and found nothing worth taking." : " and carried off " + ItemCount.describe(outcome.foodStolen()) + ".");
        }
        Event resolved = sim.emit(d.summary(summary));
        for (String killedId : outcome.killed()) {
            Character c = state.character(killedId);
            if (c != null) People.die(sim, killedId, c.name() + " was cut down by raiders at " + s.name() + ".",
                    List.of(r), resolved.id());
        }
        return resolved;
    }
}
