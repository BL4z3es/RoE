package io.github.bl4z3es.riseofempires.mc.job;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Plot;
import io.github.bl4z3es.riseofempires.core.model.Profession;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.sim.Stores;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import io.github.bl4z3es.riseofempires.mc.entity.CharacterEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Task M1-14: walks to a ripe crop of the settlement's farm, cuts it, replants it from carried seed, and
 * hands what it carries in at the store.
 */
public final class FarmerJob extends WorkJob {
	private enum Phase { SEEK, WALK_CROP, CUT, REPLANT }

	/** Ticks between looks at the field when nothing is ripe. PROPOSED: not in Balance yet. */
	private static final int SEEK_INTERVAL = 20;
	private static final double CROP_REACH = 1.6;

	private Phase phase = Phase.SEEK;
	private BlockPos crop;

	@Override
	public Profession profession() { return Profession.FARMER; }

	@Override
	protected void work(CharacterEntity body, RoeServer roe, Settlement st, Character me) {
		ServerLevel level = (ServerLevel) body.level();
		if (depositTrip(body, roe, st, me)) return;
		switch (phase) {
			case SEEK -> {
				if (body.carriedCount() >= Balance.CARRY_CAPACITY && startDeposit(body, roe, st)) return;
				if (level.getGameTime() % SEEK_INTERVAL != 0) return;
				BlockPos ripe = findRipe(roe, level, st, body);
				if (ripe != null) {
					crop = ripe;
					phase = Phase.WALK_CROP;
					resetWalk();
				} else {
					startDeposit(body, roe, st);
				}
			}
			case WALK_CROP -> {
				if (!ripe(level, crop)) {
					phase = Phase.SEEK;
				} else if (walk(body, crop, CROP_REACH)) {
					phase = Phase.CUT;
					resetWait();
				} else if (gaveUp()) {
					phase = Phase.SEEK;
					resetWalk();
				}
			}
			case CUT -> {
				if (!ripe(level, crop)) {
					phase = Phase.SEEK;
					resetWait();
				} else if (waitFor(body, Balance.HARVEST_TICKS)) {
					cut(body, roe, level, st, me);
					phase = Phase.REPLANT;
				}
			}
			case REPLANT -> {
				if (!waitFor(body, Balance.REPLANT_TICKS)) return;
				BlockState below = level.getBlockState(crop.below());
				if (level.getBlockState(crop).isAir() && below.is(Blocks.FARMLAND) && useOne(body, Items.WHEAT_SEEDS)) {
					level.setBlock(crop, Blocks.WHEAT.defaultBlockState(), Block.UPDATE_ALL);
				}
				phase = Phase.SEEK;
			}
		}
	}

	private static boolean ripe(ServerLevel level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		return s.getBlock() instanceof CropBlock c && c.isMaxAge(s);
	}

	private static BlockPos findRipe(RoeServer roe, ServerLevel level, Settlement st, CharacterEntity body) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (Plot p : roe.state().farm(st.id())) {
			BlockPos pos = RoeServer.pos(p.pos());
			if (!level.isLoaded(pos) || !ripe(level, pos)) continue;
			double d = pos.distToCenterSqr(body.position());
			if (d < bestDist) {
				bestDist = d;
				best = pos;
			}
		}
		return best;
	}

	private void cut(CharacterEntity body, RoeServer roe, ServerLevel level, Settlement st, Character me) {
		BlockState state = level.getBlockState(crop);
		List<ItemStack> drops = Block.getDrops(state, level, crop, null, body, ItemStack.EMPTY);
		level.destroyBlock(crop, false, body);
		for (ItemStack s : drops) body.carry(s);
		Stores.harvested(roe.state(), st.id(), me.id(), RoeServer.place(level, crop), count(drops), roe.now());
	}
}
