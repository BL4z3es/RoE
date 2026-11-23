package io.github.bl4z3es.riseofempires.gametest;

import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.model.Blueprint;
import io.github.bl4z3es.riseofempires.core.model.BlueprintBlock;
import io.github.bl4z3es.riseofempires.core.model.BuildSite;
import io.github.bl4z3es.riseofempires.core.model.Profession;
import io.github.bl4z3es.riseofempires.core.sim.Building;
import io.github.bl4z3es.riseofempires.core.sim.Jobs;
import io.github.bl4z3es.riseofempires.mc.entity.CharacterEntity;
import io.github.bl4z3es.riseofempires.mc.job.JobModule;
import io.github.bl4z3es.riseofempires.mc.world.WorldBlocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Task M1-13: a builder builds a small blueprint start to finish from the chest's materials. */
public final class BuilderGameTest {
	// Padding keeps the neighbouring test structures (walls of other tests) away from this scene.
	@GameTest(maxTicks = 3000, padding = 40)
	public void builderBuildsTheTestHut(GameTestHelper helper) {
		JobScene w = new JobScene(helper, 0);
		long now = w.roe.now();
		Blueprint hut = w.roe.content().blueprints().get("riseofempires:test_hut");
		helper.assertTrue(hut != null && hut.blocks().size() == 10, "the test blueprint did not load");
		BlockPos origin = w.at(8, 1, 8);
		// One block is already right, and one spot holds something that must be cleared first.
		w.level.setBlock(origin.east(2), Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
		w.level.setBlock(origin.offset(1, 1, 0), Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
		w.stock(Items.COBBLESTONE, 8);
		w.stock(Items.OAK_PLANKS, 8);
		Building.placeBlueprint(w.state, w.sid, hut, w.place(origin), 0, w.player, now);
		BuildSite site = Building.activeSite(w.state, w.sid).orElseThrow();

		long builder = w.people.get(0);
		Jobs.assign(w.state, builder, Profession.BUILDER, w.player, now);
		CharacterEntity body = w.spawn(builder, w.at(4, 1, 6));
		JobModule.sync(w.roe, body);

		helper.succeedWhen(() -> {
			BuildSite now2 = w.state.buildSite(site.id()).orElseThrow();
			helper.assertTrue(now2.complete(), "the site has " + now2.placed() + " of " + now2.total() + " blocks; body at " + body.position() + " carries " + body.carried());
			for (BlueprintBlock b : hut.blocks()) {
				BlockPos p = origin.offset(b.dx(), b.dy(), b.dz());
				var want = WorldBlocks.parse(w.level, b.blockState(), 0);
				var have = w.level.getBlockState(p);
				helper.assertTrue(want.isAir() ? have.isAir() : have.is(want.getBlock()), "wrong block at " + p + ": " + have);
			}
			helper.assertTrue(w.hasEvent(EventTypes.BLUEPRINT_COMPLETED), "no blueprint_completed event");
			helper.assertTrue(w.hasEvent(EventTypes.BLOCK_BUILT, builder), "no block_built event names the builder");
		});
	}
}
