package io.github.bl4z3es.riseofempires.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/** Sample in-world test proving the game-test harness runs. Delete once real game tests exist. */
public final class HarnessGameTest {
	@GameTest
	public void placesABlock(GameTestHelper helper) {
		BlockPos pos = new BlockPos(0, 1, 0);
		helper.setBlock(pos, Blocks.STONE);
		helper.assertBlockPresent(Blocks.STONE, pos);
		helper.succeed();
	}
}
