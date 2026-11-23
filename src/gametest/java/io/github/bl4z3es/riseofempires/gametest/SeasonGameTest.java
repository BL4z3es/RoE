package io.github.bl4z3es.riseofempires.gametest;

import io.github.bl4z3es.riseofempires.core.sim.Season;
import io.github.bl4z3es.riseofempires.mc.farm.SeasonalGrowth;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;

/** Task M1-08: crops obey the season. No outdoor growth in winter; indoor and summer crops grow. */
public final class SeasonGameTest {
	private static final int TICKS = 2000;
	private static final BlockPos WINTER_OUTDOORS = new BlockPos(1, 1, 1);
	private static final BlockPos SUMMER_OUTDOORS = new BlockPos(3, 1, 1);
	private static final BlockPos WINTER_ROOFED = new BlockPos(5, 1, 1);

	@GameTest
	public void cropsObeyTheSeason(GameTestHelper helper) {
		plant(helper, WINTER_OUTDOORS, false);
		plant(helper, SUMMER_OUTDOORS, false);
		plant(helper, WINTER_ROOFED, true);
		// The light engine needs some ticks to see the new blocks; wait until the roofed crop is lit enough to grow.
		helper.succeedWhen(() -> {
			int light = helper.getLevel().getMaxLocalRawBrightness(helper.absolutePos(WINTER_ROOFED));
			helper.assertTrue(light >= 9, "waiting for light under the roof (" + light + ")");
			try {
				helper.assertTrue(grownAfter(helper, WINTER_OUTDOORS, Season.WINTER) == 0, "outdoor wheat grew in winter");
				helper.assertTrue(grownAfter(helper, SUMMER_OUTDOORS, Season.SUMMER) > 0, "outdoor wheat did not grow in summer");
				helper.assertTrue(grownAfter(helper, WINTER_ROOFED, Season.WINTER) > 0, "roofed wheat did not grow in winter");
			} finally {
				SeasonalGrowth.override = null;
			}
		});
	}

	/** Plants wheat on watered farmland with a light beside it, optionally under a roof. */
	private static void plant(GameTestHelper helper, BlockPos rel, boolean roofed) {
		helper.setBlock(rel.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7));
		helper.setBlock(rel, Blocks.WHEAT.defaultBlockState());
		// A light source keeps every crop bright enough to grow, so only the season decides.
		helper.setBlock(rel.east(), Blocks.GLOWSTONE);
		if (roofed) helper.setBlock(rel.above(2), Blocks.STONE);
	}

	/** Ticks the wheat many times in the given season and returns its age. */
	private static int grownAfter(GameTestHelper helper, BlockPos rel, Season season) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(rel);
		SeasonalGrowth.override = season;
		RandomSource random = RandomSource.create(42);
		for (int i = 0; i < TICKS; i++) {
			var state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof CropBlock)) break;
			state.randomTick(level, pos, random);
		}
		SeasonalGrowth.override = null;
		return level.getBlockState(pos).getValue(CropBlock.AGE);
	}
}
