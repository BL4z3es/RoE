package io.github.bl4z3es.riseofempires.mc.job;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.model.BuildSite;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Profession;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.sim.Stores;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import io.github.bl4z3es.riseofempires.mc.entity.CharacterEntity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Task M1-14: alternates between chopping a log and mining an exposed stone within reach of the camp
 * centre, never inside a building site or the granary, and hands the goods in at the store.
 */
public final class GathererJob extends WorkJob {
	private enum Phase { SEEK, WALK, WORK }

	/** Vertical search range around the camp centre. PROPOSED: not in Balance yet. */
	private static final int VERTICAL_RANGE = 10;
	private static final int SEEK_INTERVAL = 40;
	/** Ticks a target that could not be reached is ignored. PROPOSED: not in Balance yet. */
	private static final int AVOID_TICKS = 3000;
	private static final double REACH = 2.5;

	private final Map<BlockPos, Long> avoided = new HashMap<>();
	private Phase phase = Phase.SEEK;
	private boolean wantLog = true;
	private boolean targetIsLog;
	private BlockPos target;

	@Override
	public Profession profession() { return Profession.GATHERER; }

	@Override
	protected void work(CharacterEntity body, RoeServer roe, Settlement st, Character me) {
		ServerLevel level = (ServerLevel) body.level();
		if (depositTrip(body, roe, st, me)) return;
		switch (phase) {
			case SEEK -> {
				if (body.carriedCount() >= Balance.CARRY_CAPACITY && startDeposit(body, roe, st)) return;
				if (level.getGameTime() % SEEK_INTERVAL != 0) return;
				BlockPos found = find(roe, level, st, body, wantLog);
				boolean log = wantLog;
				if (found == null) {
					found = find(roe, level, st, body, !wantLog);
					log = !wantLog;
				}
				if (found == null) {
					startDeposit(body, roe, st);
					return;
				}
				target = found;
				targetIsLog = log;
				phase = Phase.WALK;
				resetWalk();
			}
			case WALK -> {
				if (!valid(level, target, targetIsLog)) {
					phase = Phase.SEEK;
				} else if (walk(body, target, REACH)) {
					phase = Phase.WORK;
					resetWait();
				} else if (gaveUp()) {
					avoided.put(target, level.getGameTime() + AVOID_TICKS);
					phase = Phase.SEEK;
					resetWalk();
				}
			}
			case WORK -> {
				if (!valid(level, target, targetIsLog)) {
					phase = Phase.SEEK;
					resetWait();
				} else if (waitFor(body, targetIsLog ? Balance.CHOP_TICKS : Balance.MINE_TICKS)) {
					BlockState state = level.getBlockState(target);
					ItemStack tool = new ItemStack(targetIsLog ? Items.WOODEN_AXE : Items.WOODEN_PICKAXE);
					List<ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(state, level, target, null, body, tool);
					level.destroyBlock(target, false, body);
					for (ItemStack s : drops) body.carry(s);
					Stores.gathered(roe.state(), st.id(), me.id(), RoeServer.place(level, target), count(drops), roe.now());
					wantLog = !targetIsLog;
					phase = Phase.SEEK;
				}
			}
		}
	}

	private static boolean valid(ServerLevel level, BlockPos p, boolean log) {
		BlockState s = level.getBlockState(p);
		return log ? s.is(BlockTags.LOGS) : (s.is(Blocks.STONE) || s.is(Blocks.COBBLESTONE)) && exposed(level, p);
	}

	private static boolean exposed(ServerLevel level, BlockPos p) {
		for (Direction d : Direction.values()) {
			if (level.getBlockState(p.relative(d)).isAir()) return true;
		}
		return false;
	}

	private BlockPos find(RoeServer roe, ServerLevel level, Settlement st, CharacterEntity body, boolean log) {
		long now = level.getGameTime();
		avoided.values().removeIf(t -> t < now);
		BlockPos centre = RoeServer.pos(st.centre());
		int r = Balance.GATHER_RADIUS_BLOCKS;
		List<int[]> sites = siteBoxes(roe);
		BlockPos best = null;
		double bestScore = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(centre.offset(-r, -VERTICAL_RANGE, -r), centre.offset(r, VERTICAL_RANGE, r))) {
			if (!level.isLoaded(p) || avoided.containsKey(p)) continue;
			if (!valid(level, p, log)) continue;
			if (JobStore.insideGranary(st, p) || inSite(sites, p)) continue;
			double score = p.distToCenterSqr(body.position()) + 16.0 * Math.max(0, p.getY() - body.getY());
			if (score < bestScore) {
				bestScore = score;
				best = p.immutable();
			}
		}
		return best;
	}

	/** Bounding boxes (min x, y, z, max x, y, z) of every building site, one block wider on each side. */
	private static List<int[]> siteBoxes(RoeServer roe) {
		List<int[]> out = new java.util.ArrayList<>();
		for (BuildSite s : roe.state().buildSites()) {
			var bp = roe.content().blueprints().get(s.blueprintId());
			if (bp == null) continue;
			int[] box = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
			for (var b : bp.blocks()) {
				var w = io.github.bl4z3es.riseofempires.core.sim.Building.worldPos(s, b);
				box[0] = Math.min(box[0], w.x() - 1); box[1] = Math.min(box[1], w.y() - 1); box[2] = Math.min(box[2], w.z() - 1);
				box[3] = Math.max(box[3], w.x() + 1); box[4] = Math.max(box[4], w.y() + 1); box[5] = Math.max(box[5], w.z() + 1);
			}
			out.add(box);
		}
		return out;
	}

	private static boolean inSite(List<int[]> boxes, BlockPos p) {
		for (int[] b : boxes) {
			if (p.getX() >= b[0] && p.getX() <= b[3] && p.getY() >= b[1] && p.getY() <= b[4] && p.getZ() >= b[2] && p.getZ() <= b[5]) return true;
		}
		return false;
	}
}
