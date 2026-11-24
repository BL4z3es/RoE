package io.github.bl4z3es.riseofempires.gametest.client;

import io.github.bl4z3es.riseofempires.RiseOfEmpires;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.model.Blueprint;
import io.github.bl4z3es.riseofempires.core.model.BlueprintBlock;
import io.github.bl4z3es.riseofempires.core.model.BuildSite;
import io.github.bl4z3es.riseofempires.core.model.Plot;
import io.github.bl4z3es.riseofempires.core.model.Profession;
import io.github.bl4z3es.riseofempires.core.sim.Building;
import io.github.bl4z3es.riseofempires.core.sim.GameTime;
import io.github.bl4z3es.riseofempires.core.sim.Jobs;
import io.github.bl4z3es.riseofempires.core.sim.Stores;
import io.github.bl4z3es.riseofempires.core.sim.Survivors;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import io.github.bl4z3es.riseofempires.mc.block.CharterItem;
import io.github.bl4z3es.riseofempires.mc.block.CharterLectern;
import io.github.bl4z3es.riseofempires.mc.blueprint.BlueprintPlacement;
import io.github.bl4z3es.riseofempires.mc.stockpile.StockpileBlocks;
import io.github.bl4z3es.riseofempires.mc.world.WorldBlocks;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Task M1-16 with a real player: leave the camp for a full in-game day, so its chunks really unload,
 * and come back. A camp with a finished farm of ripe wheat, a farmer, three people and a stocked granary.
 * The player flies 2,000 blocks away, a day passes an hour at a time, the player comes back. The Ledger must show the day's work done off-camera, and the world must show it:
 * crops harvested and replanted in the fields, bread eaten from the chests, the harvest in the chests.
 */
public final class AwayForADayClientGameTest implements FabricClientGameTest {
	private static final int AWAY_BLOCKS = 2_000;
	private static final int BREAD = 64;
	private static final int HOURS_AWAY = 24;
	/** Food values from data/riseofempires/food_values.json, in quarter-meals. */
	private static final int BREAD_QUARTERS = 12;
	private static final int WHEAT_QUARTERS = 4;
	private static final int SETTLE_TICKS = 200;
	/** Where the player stops on the way home: close enough for the camp to load, beyond the Embodied radius. */
	private static final int RETURN_VIEW_BLOCKS = 70;
	/** Real ticks between hours: the simulation steps once a second. */
	private static final int TICKS_BETWEEN_HOURS = 25;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			var server = world.getServer();
			context.waitTicks(100);
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("gamerule advance_time true"); // the test world's consistent settings stop the clock
			server.runCommand("weather clear");
			server.runCommand("gamemode creative @a");
			// Day 1 morning, spring: well clear of the raid season and of winter.
			server.runCommand("time set " + (GameTime.TICKS_PER_DAY + 1_000));

			long[] ids = server.computeOnServer(s -> {
				ServerLevel level = s.overworld();
				var roe = RoeServer.get().orElseThrow();
				var state = roe.state();
				var player = world.getConnection().getServerPlayer();
				String uuid = player.getStringUUID();
				BlockPos feet = player.blockPosition();
				BlockPos lectern = new BlockPos(feet.getX() + 2, level.getHeight(Heightmap.Types.MOTION_BLOCKING, feet.getX() + 2, feet.getZ()), feet.getZ());
				level.setBlockAndUpdate(lectern, Blocks.LECTERN.defaultBlockState());
				CharterLectern.found(level, player, new ItemStack(CharterItem.ITEM), lectern);
				long sid = state.settlementFoundedBy(uuid).orElseThrow(() -> new AssertionError("the camp was not founded"));
				long now = roe.now();
				// Three people, already arrived (their walk is NothingPopsInClientGameTest's business).
				Survivors.setOut(state, sid, now, roe.content().survivors());
				List<Long> people = Survivors.pending(state, sid);
				for (long id : people) Survivors.join(state, id, sid, now, RoeServer.place(level, lectern));
				// A granary of two chests: bread to eat, room for the harvest.
				BlockPos seal = lectern.west(20); // clear of the founder's camp, which may stand up to 13 blocks from the lectern
				level.setBlockAndUpdate(seal, StockpileBlocks.GRANARY_SEAL.defaultBlockState());
				level.setBlockAndUpdate(seal.east(), Blocks.CHEST.defaultBlockState());
				level.setBlockAndUpdate(seal.west(), Blocks.CHEST.defaultBlockState());
				((Container) level.getBlockEntity(seal.east())).setItem(0, new ItemStack(Items.BREAD, BREAD));
				Stores.sealGranary(state, sid, RoeServer.place(level, seal), uuid, now);
				// The farm, placed as a player places it and standing complete, as builders leave it.
				BlockPos ground = new BlockPos(lectern.getX() - 20, lectern.getY() - 1, lectern.getZ() + 10); // beside the granary, clear of the camp
				var placed = BlueprintPlacement.place(roe, level, ground, uuid, Blueprint.FARM, 0);
				if (placed.outcome() != BlueprintPlacement.Outcome.PLACED) throw new AssertionError("farm not placed: " + placed.outcome());
				BuildSite site = Building.activeSite(state, sid).orElseThrow();
				Blueprint farm = roe.content().blueprints().get(Blueprint.FARM);
				for (BlueprintBlock b : farm.blocks()) {
					BlockPos p = RoeServer.pos(Building.worldPos(site, b));
					level.setBlock(p, WorldBlocks.parse(level, b.blockState(), 0), Block.UPDATE_ALL);
				}
				Building.blocksBuilt(state, site.id(), site.total(), people.get(0), now, roe.content().blueprints());
				// Every plot ripe, in the world (the farm is read from the world when the camp unloads).
				for (Plot plot : state.farm(sid)) {
					level.setBlock(RoeServer.pos(plot.pos()), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE), Block.UPDATE_ALL);
				}
				Jobs.assign(state, people.get(0), Profession.FARMER, uuid, now);
				// What placing the seal by hand does (GranarySeal.setPlacedBy): count the chests at once.
				io.github.bl4z3es.riseofempires.mc.stockpile.StockpileSync.recountNow(roe, state.settlement(sid).orElseThrow());
				return new long[] {sid, seal.getX(), seal.getY(), seal.getZ(), state.farm(sid).size()};
			});
			long sid = ids[0];
			BlockPos seal = new BlockPos((int) ids[1], (int) ids[2], (int) ids[3]);
			if (ids[4] < 10) throw new AssertionError("the farm registered only " + ids[4] + " plots");
			// Let the camp settle for a while before leaving, as a player would (the farm alone takes builders thousands of ticks).
			context.waitTicks(SETTLE_TICKS);
			String beforeLeaving = server.computeOnServer(s -> {
				var st = RoeServer.get().orElseThrow().state();
				java.util.Map<String, Long> types = new java.util.TreeMap<>();
				for (Event e : st.ledger().all()) types.merge(e.type(), 1L, Long::sum);
				return "store " + st.stockpile(sid).counted() + ", events so far " + types + ", now " + RoeServer.get().orElseThrow().now();
			});
			RiseOfEmpires.LOGGER.info("[AwayForADay] before leaving: {}", beforeLeaving);
			int breadBefore = count(server, seal, Items.BREAD);
			int seedsBefore = count(server, seal, Items.WHEAT_SEEDS);

			// Leave. The camp's chunks unload and it drops to the Ledger tier.
			server.runCommand("tp @a " + (seal.getX() + AWAY_BLOCKS) + " 120 " + seal.getZ());
			server.waitFor(s -> !RoeServer.get().orElseThrow().isEmbodied(RoeServer.place(s.overworld(), seal)), 2_400);
			long leftAt = server.computeOnServer(s -> RoeServer.get().orElseThrow().now());
			context.waitTicks(60); // the farm is surveyed on the next simulation step after the camp leaves the Embodied tier
			String atLeaving = server.computeOnServer(s -> {
				var st = RoeServer.get().orElseThrow().state();
				long ripePlots = st.farm(sid).stream().filter(p -> p.age() >= CropBlock.MAX_AGE).count();
				return "plots " + st.farm(sid).size() + " ripe in state " + ripePlots + ", store " + st.stockpile(sid).counted()
						+ ", people " + st.residents(sid).stream().map(c -> c.name() + ":" + c.profession() + ":" + c.condition()).toList();
			});
			long firstAwayEvent = server.computeOnServer(s -> RoeServer.get().orElseThrow().state().ledger().all().size() + 1L);

			// A full day passes, an in-game hour at a time. Client game tests tick the server in step with a
			// software-rendered client, so a real 24,000-tick wait would take most of an hour here; each hour
			// still reaches the Ledger tier as its own step, as it does when the day passes for real.
			for (int hour = 0; hour < HOURS_AWAY; hour++) {
				server.runCommand("time add " + GameTime.TICKS_PER_HOUR);
				context.waitTicks(TICKS_BETWEEN_HOURS);
			}
			server.waitFor(s -> RoeServer.get().orElseThrow().now() >= leftAt + GameTime.TICKS_PER_DAY, 200);

			// Come back as a player walking home does: the camp loads while they are still far off, then they arrive.
			server.runCommand("tp @a " + (seal.getX() + RETURN_VIEW_BLOCKS) + " 120 " + seal.getZ());
			server.waitFor(s -> RoeServer.get().orElseThrow().isEmbodied(RoeServer.place(s.overworld(), seal)), 2_400);
			context.waitTicks(100);
			server.runCommand("tp @a " + (seal.getX() + 3) + " " + (seal.getY() + 1) + " " + (seal.getZ() + 3));
			server.waitFor(s -> RoeServer.get().orElseThrow().isEmbodied(RoeServer.place(s.overworld(), seal)), 2_400);
			context.waitTicks(200);

			String report = server.computeOnServer(s -> {
				var state = RoeServer.get().orElseThrow().state();
				List<Event> away = state.ledger().all().stream().filter(e -> e.id() >= firstAwayEvent).toList();
				long work = away.stream().filter(e -> e.type().equals(EventTypes.LEDGER_WORK)).count();
				long harvests = away.stream().filter(e -> e.type().equals(EventTypes.LEDGER_WORK))
						.filter(e -> e.effects().stream().anyMatch(f -> f.what().equals("plots_harvested"))).count();
				long meals = away.stream().filter(e -> e.type().equals(EventTypes.MEAL_EATEN)).count();
				long missed = away.stream().filter(e -> e.type().equals(EventTypes.MEAL_MISSED)).count();
				long uncaused = away.stream().filter(e -> e.type().equals(EventTypes.LEDGER_WORK) && e.causes().isEmpty()).count();
				ServerLevel level = s.overworld();
				int replanted = 0, ripe = 0;
				for (Plot plot : state.farm(sid)) {
					var b = level.getBlockState(RoeServer.pos(plot.pos()));
					if (b.is(Blocks.WHEAT)) {
						if (b.getValue(CropBlock.AGE) < CropBlock.MAX_AGE) replanted++; else ripe++;
					}
				}
				java.util.Map<String, Long> types = new java.util.TreeMap<>();
				for (Event e : away) types.merge(e.type(), 1L, Long::sum);
				String works = away.stream().filter(e -> e.type().equals(EventTypes.LEDGER_WORK) && e.effects().stream().anyMatch(f -> f.what().equals("plots_harvested") || f.what().startsWith("minecraft:"))).map(e -> e.effects().toString()).toList().toString()
						+ " after return: " + state.ledger().all().stream().filter(e -> e.id() >= firstAwayEvent && e.type().equals(EventTypes.STOCKPILE_COUNTED)).map(e -> e.effects().toString()).toList()
						+ " pending now " + state.stockpile(sid).pending();
				RiseOfEmpires.LOGGER.info("[AwayForADay] at leaving: {}; away events {}; first work {}", atLeaving, types, works);
				long wheatIn = away.stream().filter(e -> e.type().equals(EventTypes.LEDGER_WORK)).flatMap(e -> e.effects().stream())
						.filter(f -> f.what().equals("minecraft:wheat") && f.detail().startsWith("+"))
						.mapToLong(f -> Long.parseLong(f.detail().substring(1))).sum();
				long harvestedPlots = away.stream().flatMap(e -> e.effects().stream()).filter(f -> f.what().equals("plots_harvested"))
						.mapToLong(f -> Long.parseLong(f.detail().replace("+", ""))).sum();
				return work + "," + harvests + "," + meals + "," + missed + "," + uncaused + "," + replanted + "," + ripe + "," + harvestedPlots + "," + wheatIn;
			});
			String[] r = report.split(",");
			int breadAfter = count(server, seal, Items.BREAD);
			int wheatAfter = count(server, seal, Items.WHEAT);
			int seedsAfter = count(server, seal, Items.WHEAT_SEEDS);
			String summary = String.format("work events %s, with harvests %s, meals eaten %s, missed %s, work events without causes %s; plots young in the world %s, ripe %s, harvested in the Ledger %s; wheat harvested %s, in the chests now %d; bread %d -> %d, seeds %d -> %d",
					r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7], r[8], wheatAfter, breadBefore, breadAfter, seedsBefore, seedsAfter);
			RiseOfEmpires.LOGGER.info("[AwayForADay] {}", summary);
			if (Long.parseLong(r[1]) < 1) throw new AssertionError("no harvest was recorded while away: " + summary);
			if (Long.parseLong(r[2]) < 2) throw new AssertionError("fewer than the day's two meals were eaten while away: " + summary);
			if (Long.parseLong(r[4]) > 0) throw new AssertionError("Ledger work without causes: " + summary);
			if (Integer.parseInt(r[5]) < 1) throw new AssertionError("no plot in the world was harvested and replanted: " + summary);
			// The day's meals came out of the store: bread, or the fresh wheat (food too) before it reached the chests.
			int eatenQuarters = (breadBefore - breadAfter) * BREAD_QUARTERS + (int) (Long.parseLong(r[8]) - wheatAfter) * WHEAT_QUARTERS;
			if (eatenQuarters <= 0) throw new AssertionError("no food left the store: " + summary);
			// Wheat is food and may be eaten as it comes in; the harvest's seeds are not, so they show it reached the store.
			if (seedsAfter <= seedsBefore) throw new AssertionError("no harvest reached the chests: " + summary);
			// Ripe crops left in the world must have been harvested in the Ledger, not reset: the world was all ripe.
			if (Integer.parseInt(r[5]) > Long.parseLong(r[7]))
				throw new AssertionError("crops changed in the world without a harvest to account for them: " + summary);
		}
	}

	private static int count(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server, BlockPos seal, Item item) {
		return server.computeOnServer(s -> {
			int n = 0;
			for (BlockPos p : new BlockPos[] {seal.east(), seal.west()}) {
				Container c = (Container) s.overworld().getBlockEntity(p);
				if (c == null) continue;
				for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
			}
			return n;
		});
	}
}
