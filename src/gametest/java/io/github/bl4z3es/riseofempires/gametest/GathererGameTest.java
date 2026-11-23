package io.github.bl4z3es.riseofempires.gametest;

import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.model.Profession;
import io.github.bl4z3es.riseofempires.core.sim.Jobs;
import io.github.bl4z3es.riseofempires.mc.entity.CharacterEntity;
import io.github.bl4z3es.riseofempires.mc.job.JobModule;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Task M1-14: a gatherer brings wood and stone to the store. */
public final class GathererGameTest {
	// Padding keeps the neighbouring test structures (walls of other tests) away from this scene.
	@GameTest(maxTicks = 3000, padding = 40)
	public void gathererFetchesWoodAndStone(GameTestHelper helper) {
		JobScene w = new JobScene(helper, 0);
		long now = w.roe.now();
		BlockPos log = w.at(9, 1, 7), stone = w.at(9, 1, 11);
		w.level.setBlock(log, Blocks.OAK_LOG.defaultBlockState(), Block.UPDATE_ALL);
		w.level.setBlock(stone, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
		long id = w.people.get(0);
		Jobs.assign(w.state, id, Profession.GATHERER, w.player, now);
		CharacterEntity body = w.spawn(id, w.at(5, 1, 6));
		JobModule.sync(w.roe, body);
		helper.succeedWhen(() -> {
			helper.assertTrue(w.inChest(Items.OAK_LOG) > 0 && w.inChest(Items.COBBLESTONE) > 0,
					"the chest holds no wood and stone yet; body at " + body.position() + " carries " + body.carried());
			helper.assertTrue(w.hasEvent(EventTypes.GOODS_GATHERED, id), "no goods_gathered event names the gatherer");
			helper.assertTrue(w.hasEvent(EventTypes.GOODS_DEPOSITED, id), "no goods_deposited event names the gatherer");
			helper.assertTrue(w.level.getBlockState(log).isAir() && w.level.getBlockState(stone).isAir(), "the blocks were not removed");
		});
	}
}
