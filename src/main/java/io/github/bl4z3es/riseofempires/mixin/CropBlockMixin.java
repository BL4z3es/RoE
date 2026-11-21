package io.github.bl4z3es.riseofempires.mixin;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.sim.Season;
import io.github.bl4z3es.riseofempires.mc.farm.SeasonalGrowth;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Crops obey the season: no outdoor growth in winter, an extra chance to grow in spring. */
@Mixin(CropBlock.class)
public abstract class CropBlockMixin {
	@Unique
	private static final ThreadLocal<Boolean> ROE_EXTRA = ThreadLocal.withInitial(() -> false);

	@Shadow
	protected abstract void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random);

	@Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
	private void roe$winterStopsGrowth(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		if (ROE_EXTRA.get()) return;
		if (SeasonalGrowth.currentSeason(level) == Season.WINTER && level.canSeeSky(pos)
				&& random.nextDouble() >= Balance.WINTER_GROWTH_MULTIPLIER) {
			ci.cancel();
		}
	}

	@Inject(method = "randomTick", at = @At("RETURN"))
	private void roe$springGrowsFaster(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		if (ROE_EXTRA.get() || SeasonalGrowth.currentSeason(level) != Season.SPRING || !level.canSeeSky(pos)) return;
		if (random.nextDouble() >= Balance.SPRING_GROWTH_MULTIPLIER - 1.0) return;
		BlockState now = level.getBlockState(pos);
		if (now.getBlock() != (Object) this) return;
		ROE_EXTRA.set(true);
		try {
			randomTick(now, level, pos, random);
		} finally {
			ROE_EXTRA.set(false);
		}
	}
}
