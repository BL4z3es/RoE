package io.github.bl4z3es.riseofempires.mc.farm;

import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Plot;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import io.github.bl4z3es.riseofempires.mc.world.DeferredEdits;
import io.github.bl4z3es.riseofempires.mc.world.WorldBlocks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Moves a farm's crop ages between the world and the core when its settlement goes off-camera and
 * comes back (task M1-16). While the camp is loaded the world's ages are read each step into a quiet
 * copy (no events). Leaving: that copy is written to the core in one event. Reading the world at the
 * moment of leaving does not work: by the time the camp stops being entity-ticking its chunks are
 * usually unloading, so nothing could be read, the Ledger tier worked from stale ages, and on return
 * those stale ages were written over the real, ripe crops. Returning: the core's ages are written to
 * the world, unseen if a player is near.
 */
public final class FarmSync implements RoeServer.Module {
	/** Ledger event type: a farm's crop ages were read from the world as its settlement left the Embodied tier. */
	public static final String FARM_SURVEYED = "farm_surveyed";

	public static final FarmSync INSTANCE = new FarmSync();

	private final Map<Long, Boolean> wasEmbodied = new HashMap<>();
	/** Settlement id to the crop ages last seen in the world, by plot index, while the camp was loaded. */
	private final Map<Long, int[]> lastSeen = new HashMap<>();

	private FarmSync() {}

	public static void register() {
		RoeServer.addModule(INSTANCE);
	}

	@Override
	public void tick(RoeServer roe, long now) {
		SimState state = roe.state();
		for (Settlement s : state.settlements()) {
			boolean embodied = roe.isEmbodied(s.centre());
			Boolean before = wasEmbodied.put(s.id(), embodied);
			if (embodied) look(roe, state, s);
			if (before == null || before == embodied) continue;
			if (before) survey(state, s, now);
			else restore(roe, state, s);
		}
	}

	/** Reads the ages of the farm's loaded crops into the quiet copy. Plots not loaded keep their last value. */
	private void look(RoeServer roe, SimState state, Settlement s) {
		List<Plot> plots = state.farm(s.id());
		int[] seen = lastSeen.get(s.id());
		if (seen == null || seen.length != plots.size()) {
			seen = new int[plots.size()];
			for (int i = 0; i < seen.length; i++) seen[i] = plots.get(i).age();
			lastSeen.put(s.id(), seen);
		}
		for (int i = 0; i < plots.size(); i++) {
			Plot p = plots.get(i);
			ServerLevel level = roe.level(p.pos());
			BlockPos pos = RoeServer.pos(p.pos());
			if (level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof CropBlock crop) {
				seen[i] = crop.getAge(level.getBlockState(pos));
			}
		}
	}

	/** The camp left the Embodied tier: the ages last seen in the world become the core's. */
	private void survey(SimState state, Settlement s, long now) {
		List<Plot> plots = state.farm(s.id());
		int[] seen = lastSeen.remove(s.id());
		if (seen == null || seen.length != plots.size()) return;
		List<Plot> next = new ArrayList<>();
		boolean changed = false;
		for (int i = 0; i < plots.size(); i++) {
			Plot p = plots.get(i);
			next.add(seen[i] == p.age() ? p : p.withAge(seen[i]));
			if (seen[i] != p.age()) changed = true;
		}
		if (!changed) return;
		Event e = state.ledger().append(EventDraft.of(FARM_SURVEYED, now)
				.at(s.centre())
				.actor(Ref.settlement(s.id()))
				.effect(Ref.settlement(s.id()), "farm", "crop ages read from the world")
				.cause(s.foundedEventId()));
		state.putFarm(s.id(), next, e);
	}

	private static void restore(RoeServer roe, SimState state, Settlement s) {
		for (Plot p : state.farm(s.id())) {
			ServerLevel level = roe.level(p.pos());
			BlockPos pos = RoeServer.pos(p.pos());
			if (!level.isLoaded(pos)) continue;
			BlockState current = level.getBlockState(pos);
			if (!(current.getBlock() instanceof CropBlock crop) || crop.getAge(current) == p.age()) continue;
			int age = Math.min(p.age(), crop.getMaxAge());
			BlockState target = crop.getStateForAge(age);
			if (WorldBlocks.playerNear(level, pos)) {
				String text = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(crop) + "["
						+ "age" + "=" + age + "]";
				DeferredEdits.INSTANCE.add(new DeferredEdits.Edit(p.pos().dimension(), p.pos().x(), p.pos().y(), p.pos().z(), text, 0));
			} else {
				level.setBlock(pos, target, net.minecraft.world.level.block.Block.UPDATE_ALL);
			}
		}
	}
}
