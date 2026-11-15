package io.github.bl4z3es.riseofempires.core.sim.camp;

import io.github.bl4z3es.riseofempires.core.Wire;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.sim.Effects;
import io.github.bl4z3es.riseofempires.core.sim.Simulation;
import io.github.bl4z3es.riseofempires.core.sim.WorldState;
import java.util.ArrayList;
import java.util.List;

/**
 * Places where builders are putting something up. A site is an ordered list of steps; each step
 * is some number of one item placed as blocks. The blueprint code in the Minecraft layer decides
 * which blocks each step places; here a step is only its cost and its time.
 */
public final class BuildSites {
    private BuildSites() {}

    public static final String KIND = "site";
    public static final String OPENED = "build_site_opened";
    public static final String FINISHED = "building_finished";

    /** Site tags. */
    public static final String SETTLEMENT = "settlement";
    public static final String NAME = "name";
    public static final String STEPS = "steps";
    /** Site tag: blocks placed by each step, as a list; drives how long a step takes. */
    public static final String STEP_BLOCKS = "step_blocks";
    /** Site tag: the blueprint id the site was opened from, or "" for a bare list of steps. */
    public static final String BLUEPRINT = "blueprint";
    /** Site stat: steps finished. */
    public static final String PROGRESS = "progress";
    /** Site stat: id of the event that opened it. */
    public static final String OPENED_EVENT = "opened_event";
    /** Settlement tag: the settlement's open sites, as a list. */
    public static final String SITES = "sites";

    public record Site(String id, String settlementId, String name, List<ItemCount> steps, List<Integer> stepBlocks,
                       String blueprint, int progress) {
        public boolean finished() {
            return progress >= steps.size();
        }

        public ItemCount nextStep() {
            return finished() ? null : steps.get(progress);
        }

        /** Blocks the next step places: its declared size, or its item count for a bare site. */
        public int nextStepBlocks() {
            if (finished()) return 0;
            return progress < stepBlocks.size() ? stepBlocks.get(progress) : steps.get(progress).count();
        }
    }

    public static Ref ref(String siteId) {
        return new Ref(KIND, siteId);
    }

    /** Opens a site. Builders of that settlement start on it once open. */
    public static Event open(Simulation sim, String settlementId, String siteId, String name,
                             List<ItemCount> steps, long cause) {
        return open(sim, settlementId, siteId, name, steps, List.of(), "", cause);
    }

    /** Opens a site with the number of blocks in each step and the blueprint it comes from. */
    public static Event open(Simulation sim, String settlementId, String siteId, String name,
                             List<ItemCount> steps, List<Integer> stepBlocks, String blueprint, long cause) {
        Settlement s = sim.state().settlement(settlementId);
        if (s == null || steps.isEmpty() || site(sim.state(), siteId) != null) return null;
        Ref ref = ref(siteId);
        List<String> sites = new ArrayList<>(Wire.unlist(sim.state().tag(Ref.settlement(settlementId), SITES)));
        sites.add(siteId);
        return sim.emit(EventDraft.of(OPENED).at(s.centre()).actor(Ref.settlement(settlementId)).actor(ref).cause(cause)
                .effect(Effects.setTag(ref, SETTLEMENT, settlementId))
                .effect(Effects.setTag(ref, NAME, name))
                .effect(Effects.setTag(ref, STEPS, Wire.list(ItemCount.encodeAll(steps))))
                .effect(Effects.setTag(ref, STEP_BLOCKS, Wire.list(stepBlocks.stream().map(String::valueOf).toList())))
                .effect(Effects.setTag(ref, BLUEPRINT, blueprint))
                .effect(Effects.setStat(ref, OPENED_EVENT, sim.ledger().size() + 1L))
                .effect(Effects.setTag(Ref.settlement(settlementId), SITES, Wire.list(sites)))
                .summary("Work began on a " + name + " at " + s.name() + "."));
    }

    public static Site site(WorldState state, String siteId) {
        Ref ref = ref(siteId);
        String settlement = state.tag(ref, SETTLEMENT);
        if (settlement.isEmpty()) return null;
        return new Site(siteId, settlement, state.tag(ref, NAME),
                ItemCount.decodeAll(Wire.unlist(state.tag(ref, STEPS))),
                Wire.unlist(state.tag(ref, STEP_BLOCKS)).stream().map(Integer::parseInt).toList(),
                state.tag(ref, BLUEPRINT), (int) state.stat(ref, PROGRESS));
    }

    /** Unfinished sites of a settlement, oldest first. */
    public static List<Site> open(WorldState state, String settlementId) {
        List<Site> out = new ArrayList<>();
        for (String id : Wire.unlist(state.tag(Ref.settlement(settlementId), SITES))) {
            Site site = site(state, id);
            if (site != null && !site.finished()) out.add(site);
        }
        return out;
    }
}
