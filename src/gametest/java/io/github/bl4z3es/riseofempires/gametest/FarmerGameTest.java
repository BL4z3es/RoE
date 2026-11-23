package io.github.bl4z3es.riseofempires.gametest;

import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Plot;
import io.github.bl4z3es.riseofempires.core.model.Profession;
import io.github.bl4z3es.riseofempires.core.sim.Jobs;
import io.github.bl4z3es.riseofempires.mc.entity.CharacterEntity;
import io.github.bl4z3es.riseofempires.mc.job.JobModule;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Task M1-14: an assigned farmer completes a full harvest-and-deposit cycle with no help. */
public final class FarmerGameTest {
	// Padding keeps the neighbouring test structures (walls of other tests) away from this scene.
	@GameTest(maxTicks = 3000, padding = 40)
	public void farmerHarvestsReplantsAndDeposits(GameTestHelper helper) {
		JobScene w = new JobScene(helper, 0);
		long now = w.roe.now();
		BlockPos[] crops = {w.at(8, 1, 8), w.at(8, 1, 10)};
		for (BlockPos crop : crops) {
			w.level.setBlock(crop.below(), Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_ALL);
			w.level.setBlock(crop, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7), Block.UPDATE_ALL);
		}
		w.level.setBlock(w.at(8, 0, 9), Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
		long founded = w.state.settlement(w.sid).orElseThrow().foundedEventId();
		Event registered = w.state.ledger().append(EventDraft.of("test_farm_registered", now)
				.actor(Ref.settlement(w.sid)).cause(founded).effect(Ref.settlement(w.sid), "farm", "two test plots"));
		w.state.putFarm(w.sid, List.of(new Plot(w.place(crops[0]), "minecraft:wheat", 7), new Plot(w.place(crops[1]), "minecraft:wheat", 7)), registered);

		long farmer = w.people.get(0);
		Jobs.assign(w.state, farmer, Profession.FARMER, w.player, now);
		CharacterEntity body = w.spawn(farmer, w.at(4, 1, 6));
		JobModule.sync(w.roe, body);
		helper.assertTrue(body.job() != null, "the farmer got no job");

		helper.succeedWhen(() -> {
			helper.assertTrue(w.inChest(Items.WHEAT) > 0, "the chest holds no wheat yet; body at " + body.position() + " carries " + body.carried());
			helper.assertTrue(w.hasEvent(EventTypes.GOODS_DEPOSITED, farmer), "no goods_deposited event names the farmer");
			helper.assertTrue(w.hasEvent(EventTypes.CROP_HARVESTED, farmer), "no crop_harvested event names the farmer");
			for (BlockPos crop : crops) {
				BlockState s = w.level.getBlockState(crop);
				helper.assertTrue(s.is(Blocks.WHEAT) && s.getValue(CropBlock.AGE) <= 1, "the plot at " + crop + " was not replanted: " + s);
			}
			for (Plot p : w.state.farm(w.sid)) helper.assertTrue(p.age() == 0, "the core still has a ripe plot at " + p.pos());
		});
	}
}
