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
 * comes back (task M1-16). Leaving: the world's ages are surveyed into the core. Returning: the
 * core's ages are written to the world, unseen if a player is near.
 */
public final class FarmSync implements RoeServer.Module {
	/** Ledger event type: a farm's crop ages were read from the world as its settlement left the Embodied tier. */
	public static final String FARM_SURVEYED = "farm_surveyed";

	public static final FarmSync INSTANCE = new FarmSync();

	private final Map<Long, Boolean> wasEmbodied = new HashMap<>();

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
			if (before == null || before == embodied) continue;
			if (before) survey(roe, state, s, now);
			else restore(roe, state, s);
		}
	}

	private static void survey(RoeServer roe, SimState state, Settlement s, long now) {
		List<Plot> plots = state.farm(s.id());
		List<Plot> next = new ArrayList<>();
		boolean changed = false;
		for (Plot p : plots) {
			Plot read = p;
			ServerLevel level = roe.level(p.pos());
			BlockPos pos = RoeServer.pos(p.pos());
			if (level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof CropBlock crop) {
				int age = crop.getAge(level.getBlockState(pos));
				if (age != p.age()) {
					read = p.withAge(age);
					changed = true;
				}
			}
			next.add(read);
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
