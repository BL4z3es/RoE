package io.github.bl4z3es.riseofempires.mc.job;

import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import io.github.bl4z3es.riseofempires.mc.stockpile.GranaryStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The chests of a settlement's granary as workers see them: where they are, so a body can walk to one.
 * Searches the same box as {@link GranaryStore} (its container list is private).
 */
public final class JobStore {
	/** One container and where it stands. */
	public record Chest(BlockPos pos, Container container) {}

	private final List<Chest> chests = new ArrayList<>();

	private JobStore(ServerLevel level, BlockPos seal) {
		int r = GranaryStore.RADIUS, h = GranaryStore.HEIGHT;
		for (BlockPos p : BlockPos.betweenClosed(seal.offset(-r, -h, -r), seal.offset(r, h, r))) {
			if (!level.isLoaded(p)) continue;
			BlockEntity be = level.getBlockEntity(p);
			if (be instanceof Container c && chests.stream().noneMatch(x -> x.container() == c)) chests.add(new Chest(p.immutable(), c));
		}
	}

	/** The store of a settlement, if it has a granary seal in a loaded area and at least one chest. */
	public static Optional<JobStore> of(RoeServer roe, Settlement s) {
		if (s.granary() == null) return Optional.empty();
		ServerLevel level = roe.level(s.granary());
		BlockPos seal = RoeServer.pos(s.granary());
		if (!level.isLoaded(seal)) return Optional.empty();
		JobStore store = new JobStore(level, seal);
		return store.chests.isEmpty() ? Optional.empty() : Optional.of(store);
	}

	public List<Chest> chests() { return chests; }

	/** The chest nearest to the point. */
	public Chest nearest(Vec3 from) {
		Chest best = chests.get(0);
		for (Chest c : chests) {
			if (c.pos().distToCenterSqr(from) < best.pos().distToCenterSqr(from)) best = c;
		}
		return best;
	}

	/** True if the position is inside the granary's reach: the seal's box, one block wider. */
	public static boolean insideGranary(Settlement s, BlockPos p) {
		if (s.granary() == null) return false;
		BlockPos seal = RoeServer.pos(s.granary());
		return Math.abs(p.getX() - seal.getX()) <= GranaryStore.RADIUS + 1 && Math.abs(p.getZ() - seal.getZ()) <= GranaryStore.RADIUS + 1
				&& Math.abs(p.getY() - seal.getY()) <= GranaryStore.HEIGHT + 1;
	}
}
