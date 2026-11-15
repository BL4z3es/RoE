package io.github.bl4z3es.riseofempires.core.sim.camp;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Professions;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.model.Stockpile;
import io.github.bl4z3es.riseofempires.core.sim.Calendar;
import io.github.bl4z3es.riseofempires.core.sim.Effects;
import io.github.bl4z3es.riseofempires.core.sim.SimSystem;
import io.github.bl4z3es.riseofempires.core.sim.Simulation;
import io.github.bl4z3es.riseofempires.core.sim.WorldState;
import java.util.ArrayList;
import java.util.List;

/**
 * Farmers, gatherers and builders (tasks M1-14, M1-16). Both tiers go through the same three
 * calls, so they cannot drift apart (rule E3):
 *
 * <ol>
 *   <li>{@link #nextOrder} says what the person should do next and how long it takes;</li>
 *   <li>{@link #begin} records that they started and takes the inputs from the stockpile;</li>
 *   <li>{@link #complete} records that they finished and delivers the outputs.</li>
 * </ol>
 *
 * <p><b>Ledger tier</b> (settlement not embodied): {@link #hourly} does this itself, calling
 * {@code complete} exactly {@code hours} after {@code begin}.
 * <b>Embodied tier</b>: {@code hourly} leaves the settlement alone. The Minecraft layer calls
 * {@code nextOrder}, walks the body through the work, and calls {@code begin} and
 * {@code complete} as the body starts and finishes. A person's current order is kept in the
 * world state ({@link #currentOrder}), so it survives a reload and carries over when a player
 * walks into or out of range mid-job.
 *
 * <p>Stockpile changes here are core events. The Minecraft layer mirrors them into the real
 * containers; it must not also move the items itself.
 */
public final class Jobs implements SimSystem {
    public static final String ID = "jobs";

    public static final String ASSIGNED = "job_assigned";
    public static final String BEGUN = "work_begun";
    public static final String DONE = "work_done";
    public static final String IDLE = "work_idle";

    /** Character tag: the encoded {@link WorkOrder} in progress, or "". */
    public static final String ORDER = "job.order";
    /** Character stat: the hour the order in progress is due to finish (Ledger tier). */
    public static final String DUE_HOUR = "job.due_hour";
    /** Character stat: id of the event that started the order in progress. */
    public static final String BEGUN_EVENT = "job.begun_event";
    /** Character stat: id of the {@link #ASSIGNED} event for the current profession. */
    public static final String ASSIGNED_EVENT = "job.assigned_event";
    /** Character tag: why the person is idle, or "" when working. */
    public static final String IDLE_REASON = "job.idle";
    /** Character stats: crops in the ground and when they were planted. */
    public static final String CROPS = "farm.crops";
    public static final String PLANTED_HOUR = "farm.planted_hour";

    public static final String STEP_PLANT = "plant";
    public static final String STEP_HARVEST = "harvest";
    public static final String STEP_GATHER = "gather";
    public static final String STEP_BUILD = "build";

    /** The jobs that exist in Act I. */
    public static final List<String> ACT_ONE_JOBS = List.of(Professions.FARMER, Professions.GATHERER,
            Professions.BUILDER, Professions.FIGHTER);

    @Override
    public String id() { return ID; }

    @Override
    public void hourly(Simulation sim) {
        WorldState state = sim.state();
        for (Settlement s : List.copyOf(state.settlements())) {
            if (state.embodied(s.id())) continue;
            for (Character c : state.residents(s.id())) {
                if (c.profession().isEmpty()) continue;
                WorkOrder current = currentOrder(state, c.id());
                if (current != null) {
                    if (state.hour() >= state.stat(Ref.character(c.id()), DUE_HOUR)) complete(sim, c.id(), current);
                    continue;
                }
                WorkOrder next = nextOrder(sim, c.id());
                if (next.isIdle()) noteIdle(sim, c, next.reason());
                else begin(sim, c.id(), next);
            }
        }
    }

    /** Gives a person a profession (or "" to stand them down). The player chooses; nothing is refused. */
    public Event assign(Simulation sim, String characterId, String profession, long cause) {
        Character c = sim.state().character(characterId);
        if (c == null || !c.alive()) return null;
        Ref ref = Ref.character(characterId);
        EventDraft d = EventDraft.of(ASSIGNED).actor(ref).cause(cause)
                .effect(Effects.setProfession(characterId, profession))
                .effect(Effects.setStat(ref, ASSIGNED_EVENT, sim.ledger().size() + 1L))
                .effect(Effects.setTag(ref, IDLE_REASON, ""))
                .summary(profession.isEmpty() ? c.name() + " was stood down from work."
                        : c.name() + " was made a " + profession + ".");
        if (currentOrder(sim.state(), characterId) != null) d.effect(Effects.setTag(ref, ORDER, ""));
        Ref home = People.home(sim, characterId);
        if (home != null) d.actor(home);
        return sim.emit(d);
    }

    /** The order the person is carrying out, or null. */
    public static WorkOrder currentOrder(WorldState state, String characterId) {
        String s = state.tag(Ref.character(characterId), ORDER);
        return s.isEmpty() ? null : WorkOrder.decode(s);
    }

    /** What this person should do next. Never null: when there is nothing to do it says why. */
    public WorkOrder nextOrder(Simulation sim, String characterId) {
        WorldState state = sim.state();
        Character c = state.character(characterId);
        if (c == null || !c.alive()) return WorkOrder.idle("", "gone");
        Settlement s = state.settlement(c.settlementId());
        if (s == null) return WorkOrder.idle(c.profession(), "has no home");
        Stockpile stock = state.stockpile(s.stockpileId());
        String job = c.profession();
        Ref ref = Ref.character(characterId);
        switch (job) {
            case Professions.FARMER -> {
                int crops = (int) state.stat(ref, CROPS);
                if (crops == 0) {
                    int seeds = Math.min(stock.count(Food.SEEDS), Balance.FARM_PLOT_CROPS);
                    if (seeds == 0) return WorkOrder.idle(job, "there are no seeds in the granary");
                    return new WorkOrder(job, STEP_PLANT, Balance.PLANT_HOURS,
                            List.of(new ItemCount(Food.SEEDS, seeds)), List.of(), "", "");
                }
                long planted = (long) state.stat(ref, PLANTED_HOUR);
                if (!Farming.ripe(planted, state.hour(), true)) {
                    return WorkOrder.idle(job, Calendar.season(state.hour()) == Calendar.Season.WINTER
                            ? "the field lies frozen until spring" : "the wheat is still growing");
                }
                return new WorkOrder(job, STEP_HARVEST, Balance.HARVEST_HOURS, List.of(), List.of(
                        new ItemCount(Food.WHEAT, (int) Math.floor(crops * Balance.HARVEST_WHEAT_PER_CROP)),
                        new ItemCount(Food.SEEDS, (int) Math.floor(crops * Balance.HARVEST_SEEDS_PER_CROP))), "", "");
            }
            case Professions.GATHERER -> {
                boolean wood = stock.count(Food.OAK_LOG) * Balance.GATHER_STONE_PER_TRIP
                        <= stock.count(Food.COBBLESTONE) * Balance.GATHER_WOOD_PER_TRIP;
                ItemCount haul = wood ? new ItemCount(Food.OAK_LOG, Balance.GATHER_WOOD_PER_TRIP)
                        : new ItemCount(Food.COBBLESTONE, Balance.GATHER_STONE_PER_TRIP);
                return new WorkOrder(job, STEP_GATHER, Balance.GATHER_TRIP_HOURS, List.of(), List.of(haul), "", "");
            }
            case Professions.BUILDER -> {
                List<BuildSites.Site> sites = BuildSites.open(state, s.id());
                if (sites.isEmpty()) return WorkOrder.idle(job, "nothing has been laid out to build");
                for (BuildSites.Site site : sites) {
                    ItemCount step = site.nextStep();
                    if (stock.count(step.item()) >= step.count()) {
                        int hours = (step.count() + Balance.BUILD_BLOCKS_PER_HOUR - 1) / Balance.BUILD_BLOCKS_PER_HOUR;
                        return new WorkOrder(job, STEP_BUILD, Math.max(1, hours), List.of(step), List.of(), site.id(), "");
                    }
                }
                ItemCount want = sites.get(0).nextStep();
                return WorkOrder.idle(job, "the " + sites.get(0).name() + " needs " + want.count() + " "
                        + Food.itemName(want.item()) + " in the granary");
            }
            case Professions.FIGHTER -> {
                return WorkOrder.idle(job, "standing watch");
            }
            case "" -> {
                return WorkOrder.idle("", "nobody has given them work");
            }
            default -> {
                return WorkOrder.idle(job, "there is no " + job + "'s work in a camp this small");
            }
        }
    }

    /**
     * The person starts an order: its inputs leave the stockpile and it becomes their current
     * order. Returns null and changes nothing if the order is idle, the person is already busy,
     * or the stockpile no longer holds the inputs.
     */
    public Event begin(Simulation sim, String characterId, WorkOrder order) {
        WorldState state = sim.state();
        Character c = state.character(characterId);
        if (c == null || !c.alive() || order.isIdle() || currentOrder(state, characterId) != null) return null;
        Settlement s = state.settlement(c.settlementId());
        if (s == null) return null;
        Stockpile stock = state.stockpile(s.stockpileId());
        for (ItemCount i : order.takes()) if (stock.count(i.item()) < i.count()) return null;
        Ref ref = Ref.character(characterId);
        EventDraft d = EventDraft.of(BEGUN).at(s.centre()).actor(ref).actor(Ref.settlement(s.id()))
                .cause((long) state.stat(ref, ASSIGNED_EVENT))
                .effect(Effects.setTag(ref, ORDER, order.encode()))
                .effect(Effects.setStat(ref, DUE_HOUR, state.hour() + order.hours()))
                .effect(Effects.setStat(ref, BEGUN_EVENT, sim.ledger().size() + 1L))
                .effect(Effects.setTag(ref, IDLE_REASON, ""));
        for (ItemCount i : order.takes()) d.effect(Effects.addStock(s.stockpileId(), i.item(), -i.count()));
        if (!order.siteId().isEmpty()) d.actor(BuildSites.ref(order.siteId()));
        return sim.emit(d.summary(c.name() + " began to " + describe(state, order) + "."));
    }

    /**
     * The person finished their current order: outputs reach the stockpile, scaled by how well
     * they are ({@link Needs#outputMultiplier}). Returns null if {@code order} is not their
     * current order.
     */
    public Event complete(Simulation sim, String characterId, WorkOrder order) {
        WorldState state = sim.state();
        Character c = state.character(characterId);
        WorkOrder current = currentOrder(state, characterId);
        if (c == null || current == null || !current.equals(order)) return null;
        Ref ref = Ref.character(characterId);
        Settlement s = state.settlement(c.settlementId());
        EventDraft d = EventDraft.of(DONE).actor(ref).cause((long) state.stat(ref, BEGUN_EVENT))
                .effect(Effects.setTag(ref, ORDER, ""));
        if (s == null || !c.alive()) {
            return sim.emit(d.summary(c.name() + "'s work was left unfinished."));
        }
        d.at(s.centre()).actor(Ref.settlement(s.id()));
        double mult = Needs.outputMultiplier(state, characterId);
        List<ItemCount> delivered = new ArrayList<>();
        for (ItemCount g : order.gives()) {
            int n = (int) Math.floor(g.count() * mult);
            if (n > 0) {
                delivered.add(new ItemCount(g.item(), n));
                d.effect(Effects.addStock(s.stockpileId(), g.item(), n));
            }
        }
        String what;
        switch (order.step()) {
            case STEP_PLANT -> {
                int seeds = order.takes().get(0).count();
                d.effect(Effects.setStat(ref, CROPS, seeds)).effect(Effects.setStat(ref, PLANTED_HOUR, state.hour()));
                what = "planted " + seeds + " wheat";
            }
            case STEP_HARVEST -> {
                d.effect(Effects.setStat(ref, CROPS, 0));
                what = "harvested the wheat and carried " + ItemCount.describe(delivered) + " to the granary";
            }
            case STEP_BUILD -> {
                BuildSites.Site site = BuildSites.site(state, order.siteId());
                d.actor(BuildSites.ref(order.siteId()));
                if (site != null) d.effect(Effects.addStat(BuildSites.ref(site.id()), BuildSites.PROGRESS, 1));
                what = "laid " + ItemCount.describe(order.takes()) + " on the " + (site == null ? "site" : site.name());
            }
            default -> what = delivered.isEmpty() ? "came back empty-handed"
                    : "brought " + ItemCount.describe(delivered) + " to the granary";
        }
        if (mult < 1.0 && !order.gives().isEmpty()) what += ", slowed by " + (mult <= Balance.ILL_OUTPUT ? "illness" : "weakness");
        Event done = sim.emit(d.summary(c.name() + " " + what + "."));
        if (order.step().equals(STEP_BUILD)) {
            BuildSites.Site site = BuildSites.site(state, order.siteId());
            if (site != null && site.finished()) {
                sim.emit(EventDraft.of(BuildSites.FINISHED).at(s.centre()).actor(Ref.settlement(s.id()))
                        .actor(BuildSites.ref(site.id())).causedBy(done)
                        .cause((long) state.stat(BuildSites.ref(site.id()), BuildSites.OPENED_EVENT))
                        .summary("The " + site.name() + " at " + s.name() + " is finished."));
            }
        }
        return done;
    }

    private void noteIdle(Simulation sim, Character c, String reason) {
        Ref ref = Ref.character(c.id());
        if (sim.state().tag(ref, IDLE_REASON).equals(reason)) return;
        sim.emit(EventDraft.of(IDLE).actor(ref).cause((long) sim.state().stat(ref, ASSIGNED_EVENT))
                .effect(Effects.setTag(ref, IDLE_REASON, reason))
                .summary(c.name() + " is idle: " + reason + "."));
    }

    private static String describe(WorldState state, WorkOrder o) {
        return switch (o.step()) {
            case STEP_PLANT -> "plant " + o.takes().get(0).count() + " wheat";
            case STEP_HARVEST -> "harvest the wheat";
            case STEP_GATHER -> "gather " + Food.itemName(o.gives().get(0).item());
            case STEP_BUILD -> {
                BuildSites.Site site = BuildSites.site(state, o.siteId());
                yield "work on the " + (site == null ? "site" : site.name());
            }
            default -> o.step();
        };
    }
}
