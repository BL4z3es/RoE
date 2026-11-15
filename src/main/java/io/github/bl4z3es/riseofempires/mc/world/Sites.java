package io.github.bl4z3es.riseofempires.mc.world;

import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.sim.camp.Blueprint;
import io.github.bl4z3es.riseofempires.core.sim.camp.BuildSites;
import io.github.bl4z3es.riseofempires.core.sim.camp.Jobs;
import io.github.bl4z3es.riseofempires.core.sim.WorldState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;

/**
 * Build sites in the world: where each stands, and turning each finished step into real blocks.
 * A site's origin is its north-west bottom corner, set when a Plan is laid down.
 */
public final class Sites {
    private Sites() {}

    /** Site tag: the origin as an encoded {@link Place}. */
    public static final String ORIGIN = "origin";
    public static final String STAKED = "site_staked";
    /** How often the outline of unfinished sites is shown, in ticks. */
    private static final int OUTLINE_TICKS = 30;

    public static void register() {
        RoeWorld.onTick(w -> {
            if (w.ticks() % OUTLINE_TICKS == 0) showOutlines(w);
        });
    }

    /** Hooks the Ledger so every finished build step is put up in the world. Called once per world. */
    public static void listen(RoeWorld w) {
        w.sim().ledger().onAppend(e -> {
            if (e.type().equals(Jobs.DONE)) placeStep(w, e);
        });
    }

    public static Place origin(WorldState state, String siteId) {
        String s = state.tag(BuildSites.ref(siteId), ORIGIN);
        return s.isEmpty() ? null : Place.decode(s);
    }

    /** Finished sites of a settlement built from this blueprint, oldest first. */
    public static List<String> finished(WorldState state, String settlementId, String blueprint) {
        List<String> out = new ArrayList<>();
        for (String id : io.github.bl4z3es.riseofempires.core.Wire.unlist(state.tag(Ref.settlement(settlementId), BuildSites.SITES))) {
            BuildSites.Site site = BuildSites.site(state, id);
            if (site != null && site.finished() && site.blueprint().equals(blueprint)) out.add(id);
        }
        return out;
    }

    /** World position of one placement of a site's blueprint. */
    public static BlockPos at(Place origin, Blueprint.Placement p) {
        return new BlockPos(origin.x() + p.x(), origin.y() + p.y(), origin.z() + p.z());
    }

    private static void placeStep(RoeWorld w, Event done) {
        for (Ref actor : done.actors()) {
            if (!actor.kind().equals(BuildSites.KIND)) continue;
            BuildSites.Site site = BuildSites.site(w.state(), actor.id());
            Place origin = origin(w.state(), actor.id());
            Blueprint bp = site == null ? null : Blueprints.get(site.blueprint());
            if (bp == null || origin == null) continue;
            int stepIndex = site.progress() - 1;
            List<Blueprint.Step> steps = bp.steps();
            if (stepIndex < 0 || stepIndex >= steps.size()) continue;
            ServerLevel level = w.level(origin);
            if (level == null) continue;
            Blueprint.Step step = steps.get(stepIndex);
            for (int i = step.from(); i < step.to(); i++) {
                Blueprint.Placement p = bp.placements().get(i);
                if (p.keep()) continue;
                BlockPos pos = at(origin, p);
                if (p.blockState().contains("part=foot") || p.blockState().contains("half=upper")) {
                    // Beds and doors place their other half themselves.
                    continue;
                }
                if (p.air() && level.hasChunkAt(pos) && level.getBlockState(pos).isAir()) continue;
                if (level.hasChunkAt(pos) && w.state().embodied(site.settlementId())) {
                    Construction.place(level, pos, BlockStates.parse(p.blockState()), done.id());
                    if (p.blockState().contains("_bed[")) placeOtherHalf(level, bp, origin, i, done.id());
                    if (p.blockState().contains("_door[")) placeOtherHalf(level, bp, origin, i, done.id());
                } else {
                    w.deferred().queue(level, pos, p.blockState(), done.id());
                    if (p.blockState().contains("_bed[") || p.blockState().contains("_door[")) {
                        Blueprint.Placement other = otherHalf(bp, i);
                        if (other != null) w.deferred().queue(level, at(origin, other), other.blockState(), done.id());
                    }
                }
            }
        }
    }

    private static void placeOtherHalf(ServerLevel level, Blueprint bp, Place origin, int index, long cause) {
        Blueprint.Placement other = otherHalf(bp, index);
        if (other != null) Construction.place(level, at(origin, other), BlockStates.parse(other.blockState()), cause);
    }

    /** The foot of a bed (one block towards its foot) or the top of a door (one block up). */
    private static Blueprint.Placement otherHalf(Blueprint bp, int index) {
        Blueprint.Placement p = bp.placements().get(index);
        for (Blueprint.Placement q : bp.placements()) {
            boolean bedFoot = p.blockState().contains("part=head") && q.blockState().contains("part=foot")
                    && q.y() == p.y() && Math.abs(q.x() - p.x()) + Math.abs(q.z() - p.z()) == 1
                    && q.blockState().split("\\[")[0].equals(p.blockState().split("\\[")[0]);
            boolean doorTop = p.blockState().contains("half=lower") && q.blockState().contains("half=upper")
                    && q.x() == p.x() && q.z() == p.z() && q.y() == p.y() + 1;
            if (bedFoot || doorTop) return q;
        }
        return null;
    }

    private static void showOutlines(RoeWorld w) {
        for (var s : w.state().settlements()) {
            if (!w.state().embodied(s.id())) continue;
            for (BuildSites.Site site : BuildSites.open(w.state(), s.id())) {
                Place origin = origin(w.state(), site.id());
                Blueprint bp = Blueprints.get(site.blueprint());
                ServerLevel level = origin == null ? null : w.level(origin);
                if (bp == null || level == null) continue;
                int from = site.progress() < bp.steps().size() ? bp.steps().get(site.progress()).from() : bp.placements().size();
                int shown = 0;
                for (int i = from; i < bp.placements().size() && shown < 24; i += 3) {
                    Blueprint.Placement p = bp.placements().get(i);
                    if (p.air() || p.keep()) continue;
                    BlockPos pos = at(origin, p);
                    level.sendParticles(ParticleTypes.WAX_ON, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1, 0.2, 0.2, 0.2, 0);
                    shown++;
                }
            }
        }
    }
}
