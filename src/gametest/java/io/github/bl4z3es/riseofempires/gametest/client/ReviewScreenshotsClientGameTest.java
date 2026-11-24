package io.github.bl4z3es.riseofempires.gametest.client;

import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.model.Blueprint;
import io.github.bl4z3es.riseofempires.core.model.BlueprintBlock;
import io.github.bl4z3es.riseofempires.core.model.BuildSite;
import io.github.bl4z3es.riseofempires.core.sim.Building;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import io.github.bl4z3es.riseofempires.mc.opening.Structure;
import io.github.bl4z3es.riseofempires.mc.opening.StructureBuilder;
import io.github.bl4z3es.riseofempires.mc.opening.StructureFiles;
import io.github.bl4z3es.riseofempires.mc.world.WorldBlocks;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Review pictures for the owner (tasks M1-09, M1-10, M1-13, M1-24): a real client renders the Hollow
 * Ruin, the founder's tent and the three finished starter buildings at noon from two angles each, and
 * the mod's items in the hotbar. Not a pass/fail check of gameplay: the structures are placed whole
 * here, the way the ruin is placed off-camera, so they can be photographed. Pictures go to
 * {@code build/run/clientGameTest/screenshots/}; {@code tools/client_gametest.sh} runs it.
 */
public final class ReviewScreenshotsClientGameTest implements FabricClientGameTest {
	/** Blocks between subjects, so no picture shows the neighbour. */
	private static final int SPACING = 80;
	private static final int SHOT_WIDTH = 1280;
	private static final int SHOT_HEIGHT = 720;
	/** Fresh random-seed worlds checked for a dry, level ruin; only the first is photographed. */
	private static final int RUIN_WORLDS = 3;
	/** Where the camera waits for the ruin: farther than the Embodied radius (48), within the loaded area. */
	private static final int APPROACH_BLOCKS = 64;

	private record Subject(String name, List<int[]> cells, List<BlockPos> world) {}

	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(SHOT_WIDTH, SHOT_HEIGHT);
		for (int i = 0; i < RUIN_WORLDS; i++) generatedRuin(context, i == 0);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			var server = world.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("gamemode spectator @a");

			List<Subject> subjects = server.computeOnServer(s -> {
				ServerLevel level = s.overworld();
				List<Subject> out = new ArrayList<>();
				int x = SPACING;
				for (String name : List.of("founders_tent")) {
					Structure st = StructureFiles.load(s.getResourceManager(), name).orElseThrow();
					BlockPos origin = ground(level, x, 0);
					level.getChunkAt(origin);
					StructureBuilder.placeAll(level, origin, st, 0);
					List<BlockPos> placed = new ArrayList<>();
					for (Structure.Block b : st.blocks()) if (!b.isAir()) placed.add(StructureBuilder.worldPos(origin, b, 0));
					out.add(new Subject(name, List.of(), placed));
					x += SPACING;
				}
				var blueprints = RoeServer.get().orElseThrow().content().blueprints();
				for (String id : List.of(Blueprint.FARM, Blueprint.STABLE, Blueprint.BUNKHOUSE)) {
					Blueprint bp = blueprints.get(id);
					BlockPos o = ground(level, x, 0).below(); // the clicked ground block, as BlueprintPlacement does
					Place origin = RoeServer.place(level, o);
					BuildSite site = new BuildSite(0, 0, id, origin, 0, 0, bp.blocks().size(), 0);
					List<BlockPos> placed = new ArrayList<>();
					for (BlueprintBlock b : bp.blocks()) {
						BlockPos p = RoeServer.pos(Building.worldPos(site, b));
						level.setBlock(p, WorldBlocks.parse(level, b.blockState(), 0), Block.UPDATE_CLIENTS);
						if (!b.blockState().equals("minecraft:air")) placed.add(p);
					}
					out.add(new Subject(id, List.of(), placed));
					x += SPACING;
				}
				return out;
			});

			context.getInput().pressKey(o -> o.keyToggleGui); // F1: hide the HUD
			for (Subject subject : subjects) {
				shoot(context, world, subject, +1, "a");
				shoot(context, world, subject, -1, "b");
				eyeLevel(context, world, subject);
			}

			// The mod's items, as the player sees them in the hotbar.
			context.getInput().pressKey(o -> o.keyToggleGui); // F1 again: show the HUD
			server.runCommand("gamemode creative @a");
			server.runCommand("clear @a");
			server.runCommand("give @a riseofempires:charter");
			server.runCommand("give @a riseofempires:granary_seal");
			server.runCommand("give @a riseofempires:farm_blueprint");
			server.runCommand("give @a riseofempires:stable_blueprint");
			server.runCommand("give @a riseofempires:bunkhouse_blueprint");
			server.runCommand("tp @a " + SPACING * 7 + " -60 0 0 0");
			world.getConnection().waitForChunksRender();
			context.waitTicks(10);
			context.takeScreenshot(TestScreenshotOptions.of("m1-items-hotbar"));
		}
	}

	/**
	 * Task M1-09 in a real, normal (not flat) fresh world: the game itself places the Hollow Ruin as the
	 * chunks around it generate. Checks it stands within the set distance of spawn and photographs it.
	 */
	private static void generatedRuin(ClientGameTestContext context, boolean photograph) {
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).create()) {
			var server = world.getServer();
			world.getConnection().waitForChunksRender();
			server.runCommand("gamerule advance_time false");
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("gamemode spectator @a");
			int[] where = server.computeOnServer(s -> {
				var state = RoeServer.get().orElseThrow().state();
				String[] xz = state.fact("ruin.place").orElseThrow(() -> new AssertionError("no Hollow Ruin was placed")).split(",");
				var spawn = s.overworld().getRespawnData().globalPos().pos();
				return new int[] {Integer.parseInt(xz[0]), Integer.parseInt(xz[1]), spawn.getX(), spawn.getZ()};
			});
			double dist = Math.hypot(where[0] - where[2], where[1] - where[3]);
			if (dist < io.github.bl4z3es.riseofempires.core.Balance.RUIN_MIN_DISTANCE_BLOCKS - HollowRuinSlack.BLOCKS
					|| dist > io.github.bl4z3es.riseofempires.core.Balance.RUIN_MAX_DISTANCE_BLOCKS + HollowRuinSlack.BLOCKS) {
				throw new AssertionError("Hollow Ruin is " + dist + " blocks from spawn");
			}
			// Approach as a player walking up does, stopping beyond the Embodied radius: chunks that were
			// generated before the spot was chosen get their part only while nobody is that close.
			server.runCommand("tp @a " + (where[0] + APPROACH_BLOCKS) + " 200 " + where[1]);
			world.getConnection().waitForChunksRender();
			server.waitFor(s -> RoeServer.get().orElseThrow().state().fact("ruin.built").isPresent(), 2400);
			int y = server.computeOnServer(s -> Integer.parseInt(RoeServer.get().orElseThrow().state().fact("ruin.y").orElseThrow()));
			world.getConnection().waitForChunksRender();
			// No water inside the ruin's walls, at or just below its floor, in the world as generated.
			int wet = server.computeOnServer(s -> {
				ServerLevel level = s.overworld();
				int n = 0;
				for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) for (int dy = -2; dy <= 1; dy++) {
					if (!level.getFluidState(new BlockPos(where[0] + dx, y + dy, where[1] + dz)).isEmpty()) n++;
				}
				return n;
			});
			if (wet > 0) throw new AssertionError("the Hollow Ruin at " + where[0] + ", " + where[1] + " has " + wet + " water blocks inside it");
			if (!photograph) return;
			Structure st = server.computeOnServer(s -> StructureFiles.load(s.getResourceManager(), "hollow_ruin").orElseThrow());
			List<BlockPos> placed = new ArrayList<>();
			BlockPos origin = new BlockPos(where[0], y, where[1]);
			for (Structure.Block b : st.blocks()) if (!b.isAir()) placed.add(StructureBuilder.worldPos(origin, b, 0));
			context.getInput().pressKey(o -> o.keyToggleGui);
			Subject ruin = new Subject("hollow_ruin", List.of(), placed);
			shoot(context, world, ruin, +1, "a");
			shoot(context, world, ruin, -1, "b");
			context.getInput().pressKey(o -> o.keyToggleGui);
		}
	}

	/** Rounding of the ruin's spot to whole blocks. */
	private static final class HollowRuinSlack {
		static final int BLOCKS = 2;
	}

	/** Frames a subject from a corner, high enough to see its roof and footprint. */
	private static void shoot(ClientGameTestContext context, TestSingleplayerContext world, Subject s, int side, String tag) {
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (BlockPos p : s.world()) {
			minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
			minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
			minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
		}
		double cx = (minX + maxX + 1) / 2.0, cy = (minY + maxY + 1) / 2.0, cz = (minZ + maxZ + 1) / 2.0;
		double size = Math.max(maxX - minX, Math.max(maxZ - minZ, maxY - minY)) + 1;
		double d = size * 0.55 + 4;
		double ex = cx + side * d, ez = cz + d, ey = maxY + size * 0.35 + 3;
		world.getServer().runCommand(String.format(java.util.Locale.ROOT,
				"tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f", ex, ey, ez, cx, cy, cz));
		world.getConnection().waitForChunksRender();
		context.waitTicks(10);
		context.takeScreenshot(TestScreenshotOptions.of("m1-" + s.name() + "-" + tag));
	}

	/** As a player standing in front of it sees it: eyes 1.6 above the ground, a few blocks off the front corner. */
	private static void eyeLevel(ClientGameTestContext context, TestSingleplayerContext world, Subject s) {
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (BlockPos p : s.world()) {
			minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
			minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
			minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
		}
		double cx = (minX + maxX + 1) / 2.0, cz = (minZ + maxZ + 1) / 2.0;
		double size = Math.max(maxX - minX, maxZ - minZ) + 1;
		double d = size * 0.9 + 3;
		double feet = minY + 1;
		world.getServer().runCommand(String.format(java.util.Locale.ROOT,
				"tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f", cx + d * 0.45, feet, cz + d, cx, feet + (maxY - minY) * 0.35, cz));
		world.getConnection().waitForChunksRender();
		context.waitTicks(10);
		context.takeScreenshot(TestScreenshotOptions.of("m1-" + s.name() + "-eye"));
	}

	private static BlockPos ground(ServerLevel level, int x, int z) {
		level.getChunkAt(new BlockPos(x, 0, z));
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
	}
}
