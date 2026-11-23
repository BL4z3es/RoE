package io.github.bl4z3es.riseofempires.gametest;

import io.github.bl4z3es.riseofempires.RiseOfEmpires;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Blueprint;
import io.github.bl4z3es.riseofempires.core.model.BlueprintBlock;
import io.github.bl4z3es.riseofempires.core.model.BuildSite;
import io.github.bl4z3es.riseofempires.core.model.Profession;
import io.github.bl4z3es.riseofempires.core.sim.Building;
import io.github.bl4z3es.riseofempires.core.sim.Jobs;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import io.github.bl4z3es.riseofempires.mc.entity.CharacterEntity;
import io.github.bl4z3es.riseofempires.mc.job.JobModule;
import io.github.bl4z3es.riseofempires.mc.world.WorldBlocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import java.util.List;

/**
 * Task M1-24: several builders share one build site (rule E3 for building).
 *
 * <p>The same test hut is built twice in one settlement, at two origins the same distance from
 * the chest: first by one builder, then by two. The two-builder build must take
 * 40&ndash;65% of the one-builder build, and the Ledger's block_built events must name each of the
 * two builders for at least a quarter of the blocks. The finished hut must match the blueprint
 * block for block, in both runs.
 */
public final class SharedSiteGameTest {
	/** The test blueprint, 10 blocks: 4 cobblestone, 2 clearings, 4 oak planks. */
	private static final String TEST_BLUEPRINT = "riseofempires:test_hut";
	/** Both builds stand the same distance from the chest, in separate cleared areas. */
	private static final BlockPos REL_ORIGIN_ONE = new BlockPos(8, 1, 8);
	private static final BlockPos REL_ORIGIN_TWO = new BlockPos(24, 1, 8);

	@GameTest(maxTicks = 15_000, padding = 40)
	public void twoBuildersBuildTwiceAsFastOnOneSite(GameTestHelper helper) {
		RoeServer roe = RoeServer.get().orElseThrow();
		ServerLevel level = helper.getLevel();
		Blueprint bp = roe.content().blueprints().get(TEST_BLUEPRINT);
		helper.assertTrue(bp != null && bp.blocks().size() == 10, "the test blueprint did not load");

		JobScene w = new JobScene(helper, 1);
		long now = w.roe.now();
		// test_hut needs 4 cobblestone and 4 planks; both runs share the chest, so plenty of each.
		// Bread keeps the builders fed across meals, as in StarterBuildingsGameTest.
		w.stock(Items.COBBLESTONE, 16);
		w.stock(Items.OAK_PLANKS, 16);
		w.stock(Items.BREAD, 192);

		// stage 0: build with one builder; 1: check it; 2: build with two; 3: check both.
		int[] stage = {0};
		BlockPos[] origin = new BlockPos[1];
		long[] siteId = {-1};
		long[] started = {0};
		long[] ticks = {-1, -1};
		CharacterEntity[] single = new CharacterEntity[1];
		CharacterEntity[] pairA = new CharacterEntity[1];
		CharacterEntity[] pairB = new CharacterEntity[1];
		/** Last ledger event id before the two-builder run: its block_built events come after this. */
		long[] ledgerMark = {-1};

		helper.succeedWhen(() -> {
			if (stage[0] == 0) {
				origin[0] = w.at(REL_ORIGIN_ONE.getX(), REL_ORIGIN_ONE.getY(), REL_ORIGIN_ONE.getZ());
				Building.placeBlueprint(w.state, w.sid, bp, w.place(origin[0]), 0, w.player, now);
				siteId[0] = Building.activeSite(w.state, w.sid).orElseThrow().id();
				long builder = w.people.get(0);
				Jobs.assign(w.state, builder, Profession.BUILDER, w.player, now);
				single[0] = w.spawn(builder, w.at(5, 1, 6));
				JobModule.sync(w.roe, single[0]);
				started[0] = helper.getTick();
				stage[0] = 1;
				helper.assertTrue(false, "one-builder run started");
			}
			if (stage[0] == 1) {
				BuildSite s1 = w.state.buildSite(siteId[0]).orElseThrow();
				helper.assertTrue(s1.complete(), "one-builder run at " + s1.placed() + "/" + s1.total()
						+ "; body at " + single[0].position() + " carries " + single[0].carried());
				ticks[0] = helper.getTick() - started[0];
				assertMatchesBlueprint(helper, level, origin[0], bp);
				single[0].discard();
				stage[0] = 2;
				helper.assertTrue(false, "one-builder run done in " + ticks[0] + " ticks");
			}
			if (stage[0] == 2) {
				ledgerMark[0] = w.state.ledger().lastId();
				origin[0] = w.at(REL_ORIGIN_TWO.getX(), REL_ORIGIN_TWO.getY(), REL_ORIGIN_TWO.getZ());
				Building.placeBlueprint(w.state, w.sid, bp, w.place(origin[0]), 0, w.player, w.roe.now());
				siteId[0] = Building.activeSite(w.state, w.sid).orElseThrow().id();
				long a = w.people.get(0);
				long b = w.people.get(1);
				Jobs.assign(w.state, a, Profession.BUILDER, w.player, now);
				Jobs.assign(w.state, b, Profession.BUILDER, w.player, now);
				pairA[0] = w.spawn(a, w.at(5, 1, 6));
				pairB[0] = w.spawn(b, w.at(6, 1, 6));
				JobModule.sync(w.roe, pairA[0]);
				JobModule.sync(w.roe, pairB[0]);
				started[0] = helper.getTick();
				stage[0] = 3;
				helper.assertTrue(false, "two-builder run started");
			}
			if (stage[0] == 3) {
				BuildSite s2 = w.state.buildSite(siteId[0]).orElseThrow();
				helper.assertTrue(s2.complete(), "two-builder run at " + s2.placed() + "/" + s2.total()
						+ "; bodies at " + pairA[0].position() + " and " + pairB[0].position());
				ticks[1] = helper.getTick() - started[0];
				assertMatchesBlueprint(helper, level, origin[0], bp);
				helper.assertTrue(w.state.fact("site.done." + siteId[0]).isPresent(), "no blueprint_completed fact for the second site");

				// Each of the two builders must have placed at least a quarter of the blocks,
				// counted from this run's block_built events only.
				int total = bp.blocks().size();
				for (CharacterEntity body : List.of(pairA[0], pairB[0])) {
					long cid = body.characterId();
					long placed = w.state.ledger().all().stream()
							.filter(e -> e.id() > ledgerMark[0] && e.type().equals("block_built"))
							.filter(e -> e.actors().stream().anyMatch(
									r -> r.kind().equals(Ref.CHARACTER) && r.id().equals(Long.toString(cid))))
							.count();
					helper.assertTrue(placed >= total / 4,
							"builder " + cid + " was credited with " + placed + " of " + total + " blocks (need at least a quarter)");
				}

				double ratio = (double) ticks[1] / ticks[0];
				RiseOfEmpires.LOGGER.info("[SharedSiteGameTest] one builder {} ticks, two builders {} ticks, ratio {}",
						ticks[0], ticks[1], String.format("%.2f", ratio));
				helper.assertTrue(ratio >= 0.40 && ratio <= 0.65,
						"two builders took " + (Math.round(ratio * 100)) + "% of one builder (" + ticks[1] + " of " + ticks[0] + " ticks), want 40-65%");
				// Every assertion passed: the runnable ends, which succeeds the test.
			}
		});
	}

	/** Every blueprint block must stand where the blueprint puts it. */
	private static void assertMatchesBlueprint(GameTestHelper helper, ServerLevel level, BlockPos origin, Blueprint bp) {
		for (BlueprintBlock b : bp.blocks()) {
			BlockPos p = origin.offset(b.dx(), b.dy(), b.dz());
			var want = WorldBlocks.parse(level, b.blockState(), 0);
			var have = level.getBlockState(p);
			helper.assertTrue(want.isAir() ? have.isAir() : have.is(want.getBlock()),
					"wrong block at " + p + ": " + have + " (wanted " + want + ")");
		}
	}
}
