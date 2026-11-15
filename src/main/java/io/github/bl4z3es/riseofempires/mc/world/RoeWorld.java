package io.github.bl4z3es.riseofempires.mc.world;

import io.github.bl4z3es.riseofempires.RiseOfEmpires;
import io.github.bl4z3es.riseofempires.core.ledger.Ledger;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.sim.CampRules;
import io.github.bl4z3es.riseofempires.core.sim.Simulation;
import io.github.bl4z3es.riseofempires.core.sim.WorldState;
import io.github.bl4z3es.riseofempires.mc.McTuning;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Hosts the simulation inside a running server: one {@link WorldState} and {@link Ledger} per
 * world, loaded at start, saved with the world, and moved forward as overworld time passes.
 */
public final class RoeWorld {
    /** The rules the simulation runs. One place to change (see {@link CampRules}). */
    private static final UnaryOperator<Simulation> RULES = CampRules::install;
    private static final List<Consumer<RoeWorld>> TICKERS = new ArrayList<>();
    private static RoeWorld current;

    private final MinecraftServer server;
    private final Simulation sim;
    private final RoeSavedData data;
    private final LedgerLog log;
    private final DeferredEdits deferred;
    private long ticks;

    private RoeWorld(MinecraftServer server, Simulation sim, RoeSavedData data, LedgerLog log, DeferredEdits deferred) {
        this.server = server;
        this.sim = sim;
        this.data = data;
        this.log = log;
        this.deferred = deferred;
    }

    /** The running world, or null before the server has started. */
    public static RoeWorld get(MinecraftServer server) {
        return current != null && current.server == server ? current : null;
    }

    /** Adds work done every server tick after the simulation has moved forward. */
    public static void onTick(Consumer<RoeWorld> ticker) {
        TICKERS.add(ticker);
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(RoeWorld::start);
        ServerLifecycleEvents.BEFORE_SAVE.register((server, flush, force) -> {
            RoeWorld w = get(server);
            if (w != null) w.save();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            RoeWorld w = get(server);
            if (w != null) { w.save(); w.log.close(); current = null; }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            RoeWorld w = get(server);
            if (w != null) w.tick();
        });
    }

    private static void start(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        RoeSavedData data = server.getDataStorage().computeIfAbsent(RoeSavedData.TYPE);
        WorldState state;
        long clockHour = overworld.getOverworldClockTime() / McTuning.TICKS_PER_HOUR;
        if (data.state().isEmpty()) {
            state = new WorldState(overworld.getSeed());
            state.setHour(clockHour);
        } else {
            state = WorldState.load(data.state());
        }
        Ledger ledger = new Ledger();
        LedgerLog log = new LedgerLog(server.getWorldPath(LevelResource.DATA).resolve("roe").resolve("ledger.log"));
        try {
            log.restoreInto(ledger, data.ledgerSize());
        } catch (IOException e) {
            throw new IllegalStateException("Rise of Empires could not read its event log at " + log.file(), e);
        }
        if (ledger.size() > 0 && ledger.get(ledger.size()).hour() > state.hour()) {
            throw new IllegalStateException("Rise of Empires: the event log is ahead of the saved world state");
        }
        ledger.onAppend(log::queue);
        Simulation sim = RULES.apply(new Simulation(state, ledger));
        current = new RoeWorld(server, sim, data, log, DeferredEdits.load(data.deferred()));
        RiseOfEmpires.LOG.info("[riseofempires] world loaded: hour {}, {} events, {} settlements",
                state.hour(), ledger.size(), state.settlements().size());
    }

    public MinecraftServer server() { return server; }
    public Simulation sim() { return sim; }
    public WorldState state() { return sim.state(); }
    public DeferredEdits deferred() { return deferred; }
    public ServerLevel overworld() { return server.overworld(); }

    /** Writes the world state into saved data and flushes the event log. */
    public void save() {
        data.store(sim.state().save(), sim.ledger().size(), deferred.save());
        log.flushAndWait();
    }

    private void tick() {
        ticks++;
        long target = overworld().getOverworldClockTime() / McTuning.TICKS_PER_HOUR;
        long start = System.nanoTime();
        // Always at least one hour when behind; then only while inside the tick budget (rule E9).
        while (sim.state().hour() < target) {
            sim.advanceHour();
            if (System.nanoTime() - start > McTuning.SIM_BUDGET_NANOS_PER_TICK) break;
        }
        if (ticks % McTuning.EMBODIED_CHECK_TICKS == 0) updateEmbodied();
        for (Consumer<RoeWorld> t : TICKERS) t.accept(this);
    }

    public long ticks() { return ticks; }

    /** True when the simulation clock has caught up with the overworld clock. */
    public boolean caughtUp() {
        return sim.state().hour() >= overworld().getOverworldClockTime() / McTuning.TICKS_PER_HOUR;
    }

    private void updateEmbodied() {
        for (Settlement s : sim.state().settlements()) {
            ServerLevel level = level(s.centre());
            boolean watched = false;
            if (level != null && level.hasChunkAt(pos(s.centre()))) {
                double reach = s.claimRadius() + io.github.bl4z3es.riseofempires.core.Balance.EMBODIED_RADIUS_BLOCKS;
                for (ServerPlayer p : level.players()) {
                    double dx = p.getX() - s.centre().x(), dz = p.getZ() - s.centre().z();
                    if (dx * dx + dz * dz <= reach * reach) { watched = true; break; }
                }
            }
            sim.state().setEmbodied(s.id(), watched);
        }
    }

    /** The loaded level for a place's dimension, or null. */
    public ServerLevel level(Place place) {
        for (ServerLevel l : server.getAllLevels()) {
            if (l.dimension().identifier().toString().equals(place.dimension())) return l;
        }
        return null;
    }

    public static Place place(Level level, BlockPos pos) {
        return new Place(level.dimension().identifier().toString(), pos.getX(), pos.getY(), pos.getZ());
    }

    public static BlockPos pos(Place place) {
        return new BlockPos(place.x(), place.y(), place.z());
    }
}
