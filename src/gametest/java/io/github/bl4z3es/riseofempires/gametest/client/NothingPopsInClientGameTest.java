package io.github.bl4z3es.riseofempires.gametest.client;

import io.github.bl4z3es.riseofempires.RiseOfEmpires;
import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.model.RaidStage;
import io.github.bl4z3es.riseofempires.core.sim.GameTime;
import io.github.bl4z3es.riseofempires.core.sim.Stores;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import io.github.bl4z3es.riseofempires.mc.block.CharterItem;
import io.github.bl4z3es.riseofempires.mc.block.CharterLectern;
import io.github.bl4z3es.riseofempires.mc.entity.RoeEntities;
import io.github.bl4z3es.riseofempires.mc.raid.RaidEntities;
import io.github.bl4z3es.riseofempires.mc.stockpile.StockpileBlocks;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Law L1 with a real player and a real client (tasks M1-11 and M1-20): survivors and raiders walk in from
 * beyond sight. A player founds a camp on open, flat ground (the hardest case: nothing blocks the view),
 * the three survivors come at dusk, then the clock moves to the first autumn and the raid comes. Every
 * time the client first receives a survivor or raider it records how far away it was, whether the client
 * would draw it at that distance, and whether a line from the player's eyes to it is clear. A body that
 * arrives close enough to be drawn with a clear line to the player has popped in, and the test fails.
 */
public final class NothingPopsInClientGameTest implements FabricClientGameTest {
	/** Ticks the survivors get to walk in from {@link Balance#SURVIVOR_ORIGIN_DISTANCE_BLOCKS}. */
	private static final int ARRIVAL_TIMEOUT = 4_000;
	/** Ticks the raiders get to walk in after the sighting. */
	private static final int RAID_TIMEOUT = 6_000;
	/** A body this close to the camp's centre has arrived. */
	private static final double ARRIVED = 16.0;

	/** What the client saw the first time it had a body. */
	private record FirstSight(String kind, double distance, boolean drawable, boolean clearLine) {
		boolean poppedIn() { return drawable && clearLine; }
	}

	/** Vanilla's default render distance. Client game tests otherwise run at 5, which loads only ~90 blocks around the player. */
	private static final int RENDER_DISTANCE = 12;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(mc -> mc.options.renderDistance().set(RENDER_DISTANCE));
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			var server = world.getServer();
			context.waitTicks(400); // the server loads the area; the client need not finish downloading or rendering it (too slow in software)
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("gamerule advance_time true"); // the test world's consistent settings stop the clock
			server.runCommand("weather clear");
			server.runCommand("gamemode creative @a");
			// Day 10, late summer: the raid may come from day 12, the first autumn day (two days after founding).
			server.runCommand("time set " + (10 * GameTime.TICKS_PER_DAY + 1_000));

			BlockPos centre = server.computeOnServer(s -> {
				var level = s.overworld();
				var player = world.getConnection().getServerPlayer();
				BlockPos feet = player.blockPosition();
				BlockPos lectern = new BlockPos(feet.getX() + 2, level.getHeight(Heightmap.Types.MOTION_BLOCKING, feet.getX() + 2, feet.getZ()), feet.getZ());
				level.setBlockAndUpdate(lectern, Blocks.LECTERN.defaultBlockState());
				CharterLectern.found(level, player, new ItemStack(CharterItem.ITEM), lectern);
				var roe = RoeServer.get().orElseThrow();
				long sid = roe.state().settlementFoundedBy(player.getUUID().toString()).orElseThrow(() -> new AssertionError("the camp was not founded"));
				// A granary with food, so the survivors eat through the time jump and the raid has a target.
				BlockPos seal = lectern.north(3);
				BlockPos chest = seal.east();
				level.setBlockAndUpdate(seal, StockpileBlocks.GRANARY_SEAL.defaultBlockState());
				level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
				((Container) level.getBlockEntity(chest)).setItem(0, new ItemStack(Items.BREAD, 64));
				Stores.sealGranary(roe.state(), sid, RoeServer.place(level, seal), player.getStringUUID(), roe.now());
				return lectern;
			});
			Vec3 camp = Vec3.atCenterOf(centre);
			Map<UUID, FirstSight> seen = new LinkedHashMap<>();

			// Dusk: the survivors set out from beyond sight and walk in.
			server.runCommand("time add " + (Balance.SURVIVORS_SET_OUT_TIME_OF_DAY - 1_000 - 100));
			try {
				context.waitFor(mc -> {
					watch(mc, seen);
					return near(mc, camp, RoeEntities.CHARACTER) >= Balance.STARTING_SURVIVORS;
				}, ARRIVAL_TIMEOUT);
			} catch (AssertionError timeout) {
				throw new AssertionError("survivors did not arrive. Client saw " + seen + ". " + diagnose(server, camp), timeout);
			}

			// Day 12: the raid is planned, then sighted; the raiders come in from their camp.
			server.runCommand("time set " + (12 * GameTime.TICKS_PER_DAY + 1_000));
			server.waitFor(s -> RoeServer.get().orElseThrow().state().raids().stream().anyMatch(r -> r.stage() != RaidStage.ENDED), 200);
			// The warning comes a day ahead; the raiders set out so as to arrive on time. Skip to just before they set out.
			long setsOut = server.computeOnServer(s -> {
				var raid = RoeServer.get().orElseThrow().state().raids().stream().filter(r -> r.stage() != RaidStage.ENDED).findFirst().orElseThrow();
				return raid.arriveTime() - (long) (Balance.RAID_ORIGIN_DISTANCE_BLOCKS * Balance.WALK_TICKS_PER_BLOCK) - 200;
			});
			server.runCommand("time set " + setsOut);
			try {
				context.waitFor(mc -> {
					watch(mc, seen);
					return near(mc, camp, RaidEntities.RAIDER) >= 1;
				}, RAID_TIMEOUT);
			} catch (AssertionError timeout) {
				throw new AssertionError("raiders did not arrive. Client saw " + seen + ". " + diagnose(server, camp), timeout);
			}

			long raiders = seen.values().stream().filter(f -> f.kind().equals("raider")).count();
			long survivors = seen.values().stream().filter(f -> f.kind().equals("character")).count();
			List<String> report = new ArrayList<>();
			seen.forEach((id, f) -> report.add(String.format(java.util.Locale.ROOT, "%s %.1f blocks drawable=%s clearLine=%s", f.kind(), f.distance(), f.drawable(), f.clearLine())));
			RiseOfEmpires.LOGGER.info("[NothingPopsIn] first sightings: {}", report);
			if (survivors < Balance.STARTING_SURVIVORS) throw new AssertionError("only " + survivors + " survivors reached the client: " + report);
			if (raiders < 1) throw new AssertionError("no raider reached the client: " + report);
			List<String> popped = new ArrayList<>();
			seen.forEach((id, f) -> { if (f.poppedIn()) popped.add(f.toString()); });
			if (!popped.isEmpty()) throw new AssertionError("popped into view: " + popped);
		}
	}

	/** The server's view: time, the last Ledger events, and where every survivor and raider body is. */
	private static String diagnose(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server, Vec3 camp) {
		return server.computeOnServer(s -> {
			var state = RoeServer.get().orElseThrow().state();
			var all = state.ledger().all();
			StringBuilder b = new StringBuilder("now=" + RoeServer.get().orElseThrow().now() + " events=");
			for (int i = Math.max(0, all.size() - 15); i < all.size(); i++) b.append(all.get(i).id()).append(':').append(all.get(i).type()).append('@').append(all.get(i).time()).append(' ');
			b.append(" simDist=").append(s.getPlayerList().getSimulationDistance()).append(" viewDist=").append(s.getPlayerList().getViewDistance());
			b.append(" probe=");
			var level = s.overworld();
			for (int r : new int[] {64, 96, 136}) for (int a = 0; a < 4; a++) {
				int x = (int) Math.round(camp.x + Math.cos(a * Math.PI / 2) * r), z = (int) Math.round(camp.z + Math.sin(a * Math.PI / 2) * r);
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				BlockPos spot = new BlockPos(x, y, z);
				b.append(r).append('/').append(a).append(":tick=").append(level.isPositionEntityTicking(spot))
						.append(",near=").append(io.github.bl4z3es.riseofempires.mc.world.WorldBlocks.playerNear(level, spot))
						.append(",seen=").append(io.github.bl4z3es.riseofempires.mc.entity.CharacterBodies.seen(level, spot)).append(' ');
			}
			b.append(" bodies=");
			for (Entity e : s.overworld().getAllEntities()) {
				if (e.getType() == RoeEntities.CHARACTER || e.getType() == RaidEntities.RAIDER) {
					b.append(String.format(java.util.Locale.ROOT, "%s@%.0f ", e.getType() == RoeEntities.CHARACTER ? "c" : "r", e.position().distanceTo(camp)));
				}
			}
			return b.toString();
		});
	}

	/** Records the first sighting of every survivor and raider the client has. */
	private static void watch(Minecraft mc, Map<UUID, FirstSight> seen) {
		if (mc.level == null || mc.player == null) return;
		Vec3 eye = mc.player.getEyePosition();
		for (Entity e : mc.level.entitiesForRendering()) {
			String kind = e.getType() == RoeEntities.CHARACTER ? "character" : e.getType() == RaidEntities.RAIDER ? "raider" : null;
			if (kind == null || seen.containsKey(e.getUUID())) continue;
			double d2 = e.distanceToSqr(eye);
			boolean clear = false;
			for (Vec3 p : new Vec3[] {e.position().add(0, 0.2, 0), e.getEyePosition()}) {
				HitResult hit = mc.level.clip(new ClipContext(eye, p, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
				if (hit.getType() == HitResult.Type.MISS) clear = true;
			}
			seen.put(e.getUUID(), new FirstSight(kind, Math.sqrt(d2), e.shouldRenderAtSqrDistance(d2), clear));
		}
	}

	private static int near(Minecraft mc, Vec3 camp, net.minecraft.world.entity.EntityType<?> type) {
		if (mc.level == null) return 0;
		int n = 0;
		for (Entity e : mc.level.entitiesForRendering()) if (e.getType() == type && e.position().distanceTo(camp) <= ARRIVED) n++;
		return n;
	}
}
