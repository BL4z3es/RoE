package io.github.bl4z3es.riseofempires.mc;

import io.github.bl4z3es.riseofempires.RiseOfEmpires;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.Ledger;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import io.github.bl4z3es.riseofempires.core.model.StateCodec;
import io.github.bl4z3es.riseofempires.mc.world.DeferredEdits;
import io.github.bl4z3es.riseofempires.mc.world.LedgerFile;
import io.github.bl4z3es.riseofempires.mc.world.RoeContent;
import io.github.bl4z3es.riseofempires.mc.world.RoeSavedData;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

/**
 * The simulation for one running server: loads it, saves it and drives it from the game clock.
 *
 * <p>Every {@link #SIM_INTERVAL_TICKS} server ticks it advances the core rules to the current world
 * time, then lets each {@link Module} act on the world. Work over {@link #TICK_BUDGET_NANOS} waits
 * for the next tick (rule E9). No file or network work happens on the tick (rule E7); saving runs in
 * Minecraft's own save step.
 */
public final class RoeServer {
	/** A part of the world layer that acts after the core has advanced. */
	public interface Module {
		void tick(RoeServer roe, long now);
	}

	public static final int SIM_INTERVAL_TICKS = 20;
	public static final long TICK_BUDGET_NANOS = 2_000_000L;

	private static RoeServer current;
	private static final List<Module> MODULES = new ArrayList<>();

	private final MinecraftServer server;
	private final LedgerFile ledgerFile;
	private SimState state;
	private RoeContent content;
	private final Set<Long> sleptTonight = new HashSet<>();
	private int tickCounter;
	private long simNanosTotal;
	private long simNanosMax;
	private long simRuns;
	private int moduleCursor;

	private RoeServer(MinecraftServer server) {
		this.server = server;
		this.ledgerFile = new LedgerFile(server.getWorldPath(LevelResource.ROOT).resolve("data"));
	}

	public static Optional<RoeServer> get() { return Optional.ofNullable(current); }

	public static void addModule(Module m) { MODULES.add(m); }

	private java.nio.file.Path deferredFile() {
		return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("riseofempires").resolve("deferred.bin");
	}

	public static void register() {
		addModule(DeferredEdits.INSTANCE);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			RoeServer roe = new RoeServer(server);
			roe.load();
			current = roe;
		});
		ServerLifecycleEvents.BEFORE_SAVE.register((server, flush, force) -> { if (current != null) current.save(); });
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> { if (current != null) current.save(); });
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> current = null);
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, ok) -> {
			if (current != null && ok) current.content = RoeContent.load(server.getResourceManager());
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> { if (current != null) current.tick(); });
	}

	public MinecraftServer server() { return server; }
	public SimState state() { return state; }
	public RoeContent content() { return content; }
	public ServerLevel overworld() { return server.overworld(); }

	/** World time used by the calendar: the overworld clock, which sleeping moves forward. */
	public long now() { return server.overworld().getOverworldClockTime(); }

	/** A character slept in a bed under a roof tonight. Read when the night is judged. */
	public void markSlept(long characterId) { sleptTonight.add(characterId); }

	private void load() {
		content = RoeContent.load(server.getResourceManager());
		try {
			Ledger ledger = ledgerFile.load();
			RoeSavedData data = server.getDataStorage().computeIfAbsent(RoeSavedData.TYPE);
			if (data.bytes().length == 0) {
				state = new SimState(server.overworld().getSeed(), ledger);
			} else {
				state = StateCodec.read(new ByteArrayInputStream(data.bytes()), ledger);
			}
			DeferredEdits.INSTANCE.load(deferredFile());
			RiseOfEmpires.LOGGER.info("Loaded {} events and {} settlements", ledger.size(), state.settlements().size());
		} catch (IOException e) {
			throw new IllegalStateException("Could not load the Rise of Empires ledger at " + ledgerFile.path(), e);
		}
	}

	/** Appends new events to the log, then hands the records to saved data. Runs in Minecraft's save step. */
	public void save() {
		if (state == null) return;
		try {
			ledgerFile.append(state.ledger());
			DeferredEdits.INSTANCE.save(deferredFile());
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			StateCodec.write(out, state);
			server.getDataStorage().computeIfAbsent(RoeSavedData.TYPE).setBytes(out.toByteArray());
		} catch (IOException e) {
			RiseOfEmpires.LOGGER.error("Could not save the Rise of Empires state", e);
		}
	}

	/**
	 * Saves, then reads the state back from disk into a separate object, as a world reload would.
	 * Used by the persistence game test (task M1-02).
	 */
	public SimState saveAndReadBack() throws IOException {
		save();
		server.getDataStorage().saveAndJoin();
		Ledger ledger = new LedgerFile(server.getWorldPath(LevelResource.ROOT).resolve("data")).load();
		try (net.minecraft.world.level.storage.SavedDataStorage fresh = new net.minecraft.world.level.storage.SavedDataStorage(
				server.getWorldPath(LevelResource.DATA), server.getFixerUpper(), server.registryAccess())) {
			RoeSavedData data = fresh.computeIfAbsent(RoeSavedData.TYPE);
			return StateCodec.read(new ByteArrayInputStream(data.bytes()), ledger);
		}
	}

	private void tick() {
		if (++tickCounter < SIM_INTERVAL_TICKS) {
			return;
		}
		long start = System.nanoTime();
		if (moduleCursor == 0) {
			long now = now();
			Simulation.advance(this, now, sleptTonight);
		}
		// Modules run in turn; if the budget runs out the rest wait for the next server tick.
		long now = now();
		while (moduleCursor < MODULES.size()) {
			MODULES.get(moduleCursor++).tick(this, now);
			if (System.nanoTime() - start > TICK_BUDGET_NANOS) return;
		}
		moduleCursor = 0;
		tickCounter = 0;
		long spent = System.nanoTime() - start;
		simNanosTotal += spent;
		simRuns++;
		simNanosMax = Math.max(simNanosMax, spent);
	}

	/** Mean and worst time of one full simulation step, in microseconds, since the server started. */
	public long[] simTimingMicros() {
		return new long[] {simRuns == 0 ? 0 : simNanosTotal / simRuns / 1000, simNanosMax / 1000, simRuns};
	}

	// Conversions between core places and the world

	public static Place place(ServerLevel level, BlockPos pos) {
		return new Place(level.dimension().identifier().toString(), pos.getX(), pos.getY(), pos.getZ());
	}

	public static BlockPos pos(Place p) { return new BlockPos(p.x(), p.y(), p.z()); }

	public ServerLevel level(Place p) {
		ResourceKey<net.minecraft.world.level.Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(p.dimension()));
		ServerLevel level = server.getLevel(key);
		return level != null ? level : server.overworld();
	}

	/** True if the area around a place is loaded with entities ticking: the Embodied tier for M1. */
	public boolean isEmbodied(Place p) {
		return level(p).isPositionEntityTicking(pos(p));
	}

	/** Events written by the last advance, for modules that react to them. */
	public List<Event> recentEvents() { return Simulation.lastEvents(); }
}
