package io.github.bl4z3es.riseofempires.mc.entity;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.sim.CampRules;
import io.github.bl4z3es.riseofempires.core.sim.camp.Food;
import io.github.bl4z3es.riseofempires.core.sim.camp.ItemCount;
import io.github.bl4z3es.riseofempires.core.sim.camp.RaidOutcome;
import io.github.bl4z3es.riseofempires.core.sim.camp.Raids;
import io.github.bl4z3es.riseofempires.mc.McTuning;
import io.github.bl4z3es.riseofempires.mc.world.RoeWorld;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;

/**
 * The raid as it plays out while the camp is watched (task M1-20). After the warning, smoke rises
 * on the horizon in the raid's direction. On arrival the raiders walk in from beyond sight and
 * make for the granary. When all are down the raid is driven off; if they reach the granary and
 * hold it they take the food and go. Either way {@link Raids#resolve} records what happened.
 */
public final class RaidBattles {
    private RaidBattles() {}

    /** Ticks raiders must hold the granary to carry the food off. */
    public static final int LOOT_TICKS = 200;
    /** Blocks from the granary that count as holding it. */
    public static final double LOOT_REACH = 4.0;
    private static final int SMOKE_TICKS = 40;

    private record Battle(String raidId, String settlementId, List<BarbarianEntity> raiders, int size, int[] holding) {}

    private static final Map<String, Battle> BATTLES = new HashMap<>();

    public static void register() {
        RoeWorld.onTick(RaidBattles::tick);
    }

    private static void tick(RoeWorld w) {
        for (Settlement s : w.state().settlements()) {
            String raidId = Raids.raidOn(w.state(), s.id());
            if (raidId.isEmpty()) continue;
            String state = Raids.state(w.state(), raidId);
            ServerLevel level = w.level(s.centre());
            if (level == null) continue;
            boolean watched = w.state().embodied(s.id());
            if (state.equals(Raids.STATE_ANNOUNCED) && watched && w.ticks() % SMOKE_TICKS == 0) smoke(w, level, s, raidId);
            if (state.equals(Raids.STATE_ARRIVED) && watched && !BATTLES.containsKey(raidId)) begin(w, level, s, raidId);
            Battle b = BATTLES.get(raidId);
            if (b != null) fight(w, level, s, b, state, watched);
        }
    }

    private static BlockPos horizon(ServerLevel level, Settlement s, String direction, int distance) {
        BlockPos c = RoeWorld.pos(s.centre());
        int dx = 0, dz = 0;
        switch (direction) {
            case "north" -> dz = -distance;
            case "south" -> dz = distance;
            case "east" -> dx = distance;
            default -> dx = -distance;
        }
        return Bodies.ground(level, c.getX() + dx, c.getZ() + dz);
    }

    private static void smoke(RoeWorld w, ServerLevel level, Settlement s, String raidId) {
        String dir = w.state().tag(Raids.ref(raidId), Raids.DIRECTION);
        BlockPos p = horizon(level, s, dir, McTuning.ARRIVAL_DISTANCE_BLOCKS * 2);
        level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, 6, 1.5, 0.5, 1.5, 0.02);
    }

    private static void begin(RoeWorld w, ServerLevel level, Settlement s, String raidId) {
        int size = (int) w.state().stat(Raids.ref(raidId), Raids.SIZE);
        String dir = w.state().tag(Raids.ref(raidId), Raids.DIRECTION);
        BlockPos start = horizon(level, s, dir, McTuning.ARRIVAL_DISTANCE_BLOCKS);
        if (!level.hasChunkAt(start) || !Bodies.outOfSight(level, start)) {
            start = Bodies.edgeOfSight(level, RoeWorld.pos(s.centre()), dir.hashCode());
            if (start == null) return;
        }
        List<BarbarianEntity> raiders = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            BarbarianEntity b = ModEntities.BARBARIAN.create(level, EntitySpawnReason.EVENT);
            if (b == null) continue;
            BlockPos at = Bodies.ground(level, start.getX() + (i % 3) - 1, start.getZ() + (i / 3) - 1);
            b.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            b.raidId = raidId;
            level.addFreshEntity(b);
            raiders.add(b);
        }
        BATTLES.put(raidId, new Battle(raidId, s.id(), raiders, size, new int[] {0}));
    }

    private static void fight(RoeWorld w, ServerLevel level, Settlement s, Battle b, String state, boolean watched) {
        List<BarbarianEntity> alive = b.raiders().stream().filter(r -> r.isAlive() && !r.isRemoved()).toList();
        if (!state.equals(Raids.STATE_ARRIVED)) {
            // Settled elsewhere (the calculator, after the camp went unwatched): the raiders leave.
            leave(level, b, alive);
            return;
        }
        if (!watched) return;
        BlockPos granary = RoeWorld.pos(s.centre());
        for (BarbarianEntity r : alive) {
            if (r.getTarget() == null && r.getNavigation().isDone()) {
                r.getNavigation().moveTo(granary.getX() + 0.5, granary.getY(), granary.getZ() + 0.5, 1.0);
            }
        }
        int slain = b.size() - alive.size();
        if (alive.isEmpty()) {
            CampRules.raids(w.sim()).resolve(w.sim(), b.raidId(), new RaidOutcome(true, slain, List.of(), List.of()));
            BATTLES.remove(b.raidId());
            return;
        }
        boolean holding = alive.stream().anyMatch(r -> r.distanceToSqr(granary.getX(), granary.getY(), granary.getZ()) < LOOT_REACH * LOOT_REACH);
        b.holding()[0] = holding ? b.holding()[0] + 1 : Math.max(0, b.holding()[0] - 1);
        if (b.holding()[0] >= LOOT_TICKS) {
            int carry = alive.size() * Balance.RAID_FOOD_PER_RAIDER;
            List<ItemCount> stolen = new ArrayList<>();
            var stock = w.state().stockpile(s.stockpileId());
            for (String item : Food.MEAL_VALUES.keySet()) {
                if (carry <= 0) break;
                int n = Math.min(carry, stock.count(item));
                if (n > 0) { stolen.add(new ItemCount(item, n)); carry -= n; }
            }
            CampRules.raids(w.sim()).resolve(w.sim(), b.raidId(), new RaidOutcome(false, slain, stolen, List.of()));
        }
    }

    private static void leave(ServerLevel level, Battle b, List<BarbarianEntity> alive) {
        for (BarbarianEntity r : alive) {
            r.setTarget(null);
            if (Bodies.outOfSight(level, r.blockPosition())) { r.discard(); continue; }
            if (r.getNavigation().isDone()) {
                BlockPos away = r.blockPosition().offset((r.getX() > 0 ? 1 : -1) * McTuning.ARRIVAL_DISTANCE_BLOCKS, 0, 0);
                r.getNavigation().moveTo(away.getX(), away.getY(), away.getZ(), 1.1);
            }
        }
        if (alive.isEmpty()) BATTLES.remove(b.raidId());
    }

    /** Test hook. */
    public static int raidersIn(String raidId) {
        Battle b = BATTLES.get(raidId);
        return b == null ? 0 : (int) b.raiders().stream().filter(r -> r.isAlive()).count();
    }
}
