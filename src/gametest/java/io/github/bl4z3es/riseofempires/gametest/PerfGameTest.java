package io.github.bl4z3es.riseofempires.gametest;

import io.github.bl4z3es.riseofempires.RiseOfEmpires;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Blueprint;
import io.github.bl4z3es.riseofempires.core.model.Plot;
import io.github.bl4z3es.riseofempires.core.model.Profession;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import io.github.bl4z3es.riseofempires.core.sim.Building;
import io.github.bl4z3es.riseofempires.core.sim.Founding;
import io.github.bl4z3es.riseofempires.core.sim.Jobs;
import io.github.bl4z3es.riseofempires.core.sim.Stores;
import io.github.bl4z3es.riseofempires.core.sim.Survivors;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import io.github.bl4z3es.riseofempires.mc.entity.CharacterEntity;
import io.github.bl4z3es.riseofempires.mc.job.JobModule;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;

/**
 * Task M1-23: server tick time with a camp loaded (three survivors working in the world) and unloaded
 * (the same camp in the Ledger tier), plus a baseline with no camp. Each test only measures when the
 * environment variable {@code ROE_PERF} names it, so normal builds skip it in one tick:
 * <pre>ROE_PERF=loaded ./gradlew runGameTest</pre> (also {@code unloaded}, {@code baseline}).
 * The measured window starts after {@link #WARM_UP} ticks so the other game tests have finished.
 */
public final class PerfGameTest {
	private static final int WARM_UP = 1200;
	private static final int WINDOW = 1200;

	@GameTest(maxTicks = WARM_UP + WINDOW + 100, padding = 40)
	public void loaded(GameTestHelper helper) {
		if (!enabled("loaded", helper)) return;
		JobScene w = new JobScene(helper, 1);
		long now = w.roe.now();
		w.stock(Items.BREAD, 64);
		w.stock(Items.COBBLESTONE, 32);
		w.stock(Items.OAK_PLANKS, 32);
		// Twelve ripe plots for the farmer.
		List<Plot> plots = new ArrayList<>();
		for (int i = 0; i < 12; i++) {
			BlockPos crop = w.at(8 + (i % 6), 1, 10 + (i / 6) * 2);
			w.level.setBlock(crop.below(), Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_ALL);
			w.level.setBlock(crop, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7), Block.UPDATE_ALL);
			plots.add(new Plot(w.place(crop), "minecraft:wheat", 7));
		}
		w.level.setBlock(w.at(8, 0, 11), Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
		Event registered = w.state.ledger().append(EventDraft.of("perf_farm_registered", now).actor(Ref.settlement(w.sid))
				.cause(w.state.settlement(w.sid).orElseThrow().foundedEventId()).effect(Ref.settlement(w.sid), "farm", "12 plots"));
		w.state.putFarm(w.sid, plots, registered);
		// Something to build and something to gather.
		Blueprint hut = w.roe.content().blueprints().get("riseofempires:test_hut");
		Building.placeBlueprint(w.state, w.sid, hut, w.place(w.at(4, 1, 18)), 0, w.player, now);
		for (int i = 0; i < 6; i++) w.level.setBlock(w.at(18, 1, 4 + i * 2), Blocks.OAK_LOG.defaultBlockState(), Block.UPDATE_ALL);
		Profession[] jobs = {Profession.FARMER, Profession.BUILDER, Profession.GATHERER};
		for (int i = 0; i < 3; i++) {
			long id = w.people.get(i);
			Jobs.assign(w.state, id, jobs[i], w.player, now);
			CharacterEntity body = w.spawn(id, w.at(4 + i, 1, 6));
			JobModule.sync(w.roe, body);
		}
		measure(helper, "camp loaded (3 bodies working)");
	}

	@GameTest(maxTicks = WARM_UP + WINDOW + 100)
	public void unloaded(GameTestHelper helper) {
		if (!enabled("unloaded", helper)) return;
		RoeServer roe = RoeServer.get().orElseThrow();
		SimState s = roe.state();
		long now = roe.now();
		String player = UUID.randomUUID().toString();
		Place far = new Place("minecraft:overworld", 4_000_000, 64, 4_000_000);
		Founding.found(s, player, "Perfhold", far, now).orElseThrow();
		long sid = s.settlementFoundedBy(player).orElseThrow();
		Stores.sealGranary(s, sid, far, player, now);
		Stores.recount(s, sid, Map.of("minecraft:bread", 64, "minecraft:cobblestone", 32, "minecraft:oak_planks", 32), false, now);
		Survivors.setOut(s, sid, now, List.of(
				new Survivors.Template("Ada", "steady", "a roof", "a fire"),
				new Survivors.Template("Bram", "quick", "his brother", "a road"),
				new Survivors.Template("Cora", "proud", "land", "an empire")));
		List<Long> people = Survivors.pending(s, sid);
		for (long id : people) Survivors.join(s, id, sid, now, far);
		Profession[] jobs = {Profession.FARMER, Profession.BUILDER, Profession.GATHERER};
		for (int i = 0; i < 3; i++) Jobs.assign(s, people.get(i), jobs[i], player, now);
		List<Plot> plots = new ArrayList<>();
		for (int i = 0; i < 12; i++) plots.add(new Plot(new Place(far.dimension(), far.x() + 8 + i, 64, far.z() + 8), "minecraft:wheat", 7));
		Event registered = s.ledger().append(EventDraft.of("perf_farm_registered", now).actor(Ref.settlement(sid))
				.cause(s.settlement(sid).orElseThrow().foundedEventId()).effect(Ref.settlement(sid), "farm", "12 plots"));
		s.putFarm(sid, plots, registered);
		Building.placeBlueprint(s, sid, roe.content().blueprints().get("riseofempires:test_hut"),
				new Place(far.dimension(), far.x() + 4, 64, far.z() + 18), 0, player, now);
		measure(helper, "camp unloaded (Ledger tier)");
	}

	@GameTest(maxTicks = WARM_UP + WINDOW + 100)
	public void baseline(GameTestHelper helper) {
		if (!enabled("baseline", helper)) return;
		measure(helper, "no camp (baseline)");
	}

	private static boolean enabled(String name, GameTestHelper helper) {
		if (name.equals(System.getenv("ROE_PERF"))) return true;
		helper.succeed();
		return false;
	}

	/** Sums every server tick's duration over the window, and the simulation step's own cost. */
	private static void measure(GameTestHelper helper, String label) {
		RoeServer roe = RoeServer.get().orElseThrow();
		long[] sum = new long[3]; // total nanos, ticks, worst
		long[][] simAtStart = new long[1][];
		int[] lastIndex = {-1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (tick == WARM_UP) simAtStart[0] = roe.simTotals();
			if (tick <= WARM_UP || tick > WARM_UP + WINDOW) return;
			long[] times = roe.server().getTickTimesNanos();
			int i = roe.server().getTickCount() % times.length;
			int prev = (i - 1 + times.length) % times.length;
			if (prev == lastIndex[0]) return;
			lastIndex[0] = prev;
			sum[0] += times[prev];
			sum[1]++;
			sum[2] = Math.max(sum[2], times[prev]);
		});
		helper.runAtTickTime(WARM_UP + WINDOW, () -> {
			long[] end = roe.simTotals();
			long steps = end[1] - simAtStart[0][1];
			double simMean = steps == 0 ? 0 : (end[0] - simAtStart[0][0]) / 1000.0 / steps;
			RiseOfEmpires.LOGGER.info(String.format("PERF %s: server tick mean %.3f ms, worst %.3f ms over %d ticks; "
					+ "simulation step mean %.1f us over %d steps (worst since start %.1f us); %d characters, %d events",
					label, sum[0] / 1e6 / Math.max(1, sum[1]), sum[2] / 1e6, sum[1], simMean, steps, end[2] / 1000.0,
					roe.state().characters().size(), roe.state().ledger().size()));
			helper.succeed();
		});
	}
}
