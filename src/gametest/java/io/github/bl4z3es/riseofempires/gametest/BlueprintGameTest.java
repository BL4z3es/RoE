package io.github.bl4z3es.riseofempires.gametest;

import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.model.Blueprint;
import io.github.bl4z3es.riseofempires.core.model.BlueprintBlock;
import io.github.bl4z3es.riseofempires.core.model.BuildSite;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import io.github.bl4z3es.riseofempires.core.sim.Building;
import io.github.bl4z3es.riseofempires.core.sim.Founding;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import io.github.bl4z3es.riseofempires.mc.blueprint.BlueprintOutline;
import io.github.bl4z3es.riseofempires.mc.blueprint.BlueprintPlacement;
import io.github.bl4z3es.riseofempires.mc.world.WorldBlocks;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/** Task M1-13 (placement half): the three starter blueprints load, parse, and can be laid out in a camp. */
public final class BlueprintGameTest {
	@GameTest
	public void startersLoadAndParse(GameTestHelper helper) {
		RoeServer roe = RoeServer.get().orElseThrow();
		Map<String, Blueprint> all = roe.content().blueprints();
		ServerLevel level = helper.getLevel();
		int wheat = 0;
		int heads = 0;
		for (String id : new String[] {Blueprint.FARM, Blueprint.STABLE, Blueprint.BUNKHOUSE}) {
			Blueprint bp = all.get(id);
			helper.assertTrue(bp != null && !bp.blocks().isEmpty(), id + " did not load");
			for (BlueprintBlock b : bp.blocks()) {
				boolean air = b.blockState().equals("minecraft:air");
				BlockState state = WorldBlocks.parse(level, b.blockState(), 0);
				helper.assertTrue(air == state.isAir(), id + ": unreadable block state " + b.blockState());
				if (!b.item().isEmpty()) {
					helper.assertTrue(BuiltInRegistries.ITEM.containsKey(Identifier.parse(b.item())), id + ": unknown item " + b.item());
				}
				if (air) {
					helper.assertTrue(b.item().isEmpty(), id + ": air costs an item");
				}
				if (id.equals(Blueprint.FARM) && b.blockState().startsWith("minecraft:wheat[age=0]")) wheat++;
				if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) {
					if (id.equals(Blueprint.BUNKHOUSE)) heads++;
					Direction facing = state.getValue(BedBlock.FACING);
					helper.assertTrue(hasFoot(level, bp, b, facing), id + ": bed head at " + b.dx() + "," + b.dz() + " has no foot");
					helper.assertTrue(b.item().equals("minecraft:white_bed"), "the bed item is on the head");
				}
			}
		}
		helper.assertTrue(wheat >= 24, "farm has only " + wheat + " wheat plots");
		helper.assertTrue(heads >= 3, "bunkhouse has only " + heads + " beds");
		helper.succeed();
	}

	private static boolean hasFoot(ServerLevel level, Blueprint bp, BlueprintBlock head, Direction facing) {
		int fx = head.dx() - facing.getStepX(), fz = head.dz() - facing.getStepZ();
		for (BlueprintBlock b : bp.blocks()) {
			if (b.dx() == fx && b.dy() == head.dy() && b.dz() == fz) {
				BlockState s = WorldBlocks.parse(level, b.blockState(), 0);
				return s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.FOOT
						&& s.getValue(BedBlock.FACING) == facing && b.item().isEmpty();
			}
		}
		return false;
	}

	@GameTest
	public void placedInCampThenBuilt(GameTestHelper helper) {
		RoeServer roe = RoeServer.get().orElseThrow();
		SimState s = roe.state();
		ServerLevel level = helper.getLevel();
		long now = roe.now();
		String player = UUID.randomUUID().toString();
		// far from every other test camp: claims overlap otherwise and the lowest id wins
		BlockPos clicked = helper.absolutePos(new BlockPos(1, 1, 1)).offset(700_000, 0, 0);
		Place centre = RoeServer.place(level, clicked);
		Founding.found(s, player, "Plotford", centre, now).orElseThrow();
		long sid = s.settlementFoundedBy(player).orElseThrow();

		// outside the camp: refused, nothing written
		BlockPos far = clicked.offset(200, 0, 0);
		int sitesBefore = s.buildSites().size();
		BlueprintPlacement.Result refused = BlueprintPlacement.place(roe, level, far, player, Blueprint.FARM, 0);
		helper.assertTrue(refused.outcome() == BlueprintPlacement.Outcome.OUTSIDE_CAMP, "far-away placement was not refused");
		helper.assertTrue(s.buildSites().size() == sitesBefore, "a refused placement made a site");
		BlueprintPlacement.Result stranger = BlueprintPlacement.place(roe, level, clicked, UUID.randomUUID().toString(), Blueprint.FARM, 0);
		helper.assertTrue(stranger.outcome() == BlueprintPlacement.Outcome.OUTSIDE_CAMP, "another player's camp accepted the blueprint");

		// the farm
		BlueprintPlacement.Result farm = BlueprintPlacement.place(roe, level, clicked, player, Blueprint.FARM, 1);
		helper.assertTrue(farm.outcome() == BlueprintPlacement.Outcome.PLACED, "farm was not placed");
		helper.assertTrue(farm.event().orElseThrow().type().equals(EventTypes.BLUEPRINT_PLACED), "no blueprint_placed event");
		BuildSite site = Building.activeSite(s, sid).orElseThrow();
		helper.assertTrue(site.blueprintId().equals(Blueprint.FARM) && !site.complete() && site.rotation() == 1, "site is not an incomplete rotated farm");
		new BlueprintOutline().tick(roe, now);

		Map<String, Blueprint> all = roe.content().blueprints();
		Building.blocksBuilt(s, site.id(), site.total(), 0, now, all);
		helper.assertTrue(s.buildSite(site.id()).orElseThrow().complete(), "farm site did not complete");
		helper.assertTrue(s.farm(sid).size() >= 24, "farm plots were not registered: " + s.farm(sid).size());

		// the bunkhouse
		BlueprintPlacement.Result bunk = BlueprintPlacement.place(roe, level, clicked.offset(10, 0, 0), player, Blueprint.BUNKHOUSE, 0);
		helper.assertTrue(bunk.outcome() == BlueprintPlacement.Outcome.PLACED, "bunkhouse was not placed");
		BuildSite bsite = Building.activeSite(s, sid).orElseThrow();
		Building.blocksBuilt(s, bsite.id(), bsite.total(), 0, now, all);
		int beds = Integer.parseInt(s.fact("beds." + sid).orElse("0"));
		helper.assertTrue(beds >= 3, "bunkhouse registered only " + beds + " beds");

		// the stable
		BlueprintPlacement.Result stable = BlueprintPlacement.place(roe, level, clicked.offset(-10, 0, 0), player, Blueprint.STABLE, 2);
		helper.assertTrue(stable.outcome() == BlueprintPlacement.Outcome.PLACED, "stable was not placed");
		helper.succeed();
	}
}
