package io.github.bl4z3es.riseofempires.mc.opening;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Raises the founder's tent and campfire block by block (law L1: buildings are built, they do not
 * appear). One block every {@link #TICKS_PER_BLOCK} ticks; clearing the grass under it is instant.
 */
public final class FoundersTent {
	private FoundersTent() {}

	/** Server ticks between two blocks of the tent. */
	public static final int TICKS_PER_BLOCK = 2;
	/** How far from the lectern a flat spot is looked for, in blocks. */
	public static final int SEARCH_RADIUS = 8;
	/** The nearest the tent origin may be to the lectern. */
	public static final int MIN_DISTANCE = 3;
	/** Local camp footprint: x -2..2, z -5 (the seat across the fire) .. 4 (back of the tent). Must match founders_tent.json. */
	private static final int X_MIN = -2;
	private static final int X_MAX = 2;
	private static final int Z_MIN = -5;
	private static final int Z_MAX = 4;
	private static final String NAME = "founders_tent";

	/** Where the tent goes: origin is the floor-level block in front of the entrance pole line. */
	public record Site(BlockPos origin, int turns) {}

	private static final class Job {
		final ServerLevel level;
		final Deque<BlockPos> positions = new ArrayDeque<>();
		final Deque<BlockState> states = new ArrayDeque<>();
		int wait = TICKS_PER_BLOCK;

		Job(ServerLevel level) { this.level = level; }

		boolean step() {
			BlockPos p = positions.poll();
			BlockState s = states.poll();
			if (p == null) return true;
			// The site was cleared when the tent began. Anything standing there now was put there since,
			// most likely by the player (a chest, a crafting table): the tent goes around it, never over it.
			if (!level.getBlockState(p).canBeReplaced()) return positions.isEmpty();
			level.setBlock(p, s, Block.UPDATE_ALL);
			level.playSound(null, p, s.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.8F, 1.0F);
			return positions.isEmpty();
		}
	}

	private static final List<Job> JOBS = new ArrayList<>();

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Iterator<Job> it = JOBS.iterator();
			while (it.hasNext()) {
				Job j = it.next();
				if (--j.wait > 0) continue;
				j.wait = TICKS_PER_BLOCK;
				if (j.step()) it.remove();
			}
		});
		// A tent still going up when the world closes is finished rather than lost.
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Job j : JOBS) while (!j.step()) { /* finish */ }
			JOBS.clear();
		});
	}

	/** True while any tent is still being raised. */
	public static boolean building() { return !JOBS.isEmpty(); }

	/** Starts raising the tent. Clearing is done now; every other block follows one by one. */
	public static void raise(ServerLevel level, Structure tent, Site site) {
		Job job = new Job(level);
		for (Structure.Block b : tent.blocks()) {
			BlockPos p = StructureBuilder.worldPos(site.origin(), b, site.turns());
			BlockState s = StructureBuilder.state(level, b, site.turns());
			if (b.isAir()) {
				level.setBlock(p, s, Block.UPDATE_CLIENTS);
			} else {
				job.positions.add(p);
				job.states.add(s);
			}
		}
		if (!job.positions.isEmpty()) JOBS.add(job);
	}

	/** Loads the tent from data and raises it at a spot near the lectern. */
	public static void raiseNear(ServerLevel level, BlockPos lectern) {
		StructureFiles.load(level.getServer().getResourceManager(), NAME)
				.ifPresent(tent -> raise(level, tent, find(level, lectern)));
	}

	/**
	 * First clear, roughly flat spot within {@link #SEARCH_RADIUS} blocks of the lectern, nearest first,
	 * with the entrance turned toward the lectern when it can be. With none, the tent goes beside the lectern.
	 */
	public static Site find(ServerLevel level, BlockPos lectern) {
		for (int d = MIN_DISTANCE; d <= SEARCH_RADIUS; d++) {
			for (int ox = -d; ox <= d; ox++) {
				for (int oz = -d; oz <= d; oz++) {
					if (Math.max(Math.abs(ox), Math.abs(oz)) != d) continue;
					for (int turns = 0; turns < 4; turns++) {
						int y = fits(level, lectern, lectern.getX() + ox, lectern.getZ() + oz, turns);
						if (y != Integer.MIN_VALUE && facesLectern(turns, ox, oz)) {
							return new Site(new BlockPos(lectern.getX() + ox, y, lectern.getZ() + oz), turns);
						}
					}
				}
			}
		}
		return new Site(lectern.offset(MIN_DISTANCE, 0, 0), 0);
	}

	/** The entrance looks along local -z; true if that turned direction has a component toward the lectern. */
	private static boolean facesLectern(int turns, int ox, int oz) {
		int fx = 0;
		int fz = -1;
		for (int i = 0; i < turns; i++) {
			int nx = -fz;
			fz = fx;
			fx = nx;
		}
		return fx * -ox + fz * -oz >= 0;
	}

	/** The floor y if the footprint at this origin is clear and flat to within one block, else MIN_VALUE. */
	private static int fits(ServerLevel level, BlockPos lectern, int ox, int oz, int turns) {
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (int lx = X_MIN; lx <= X_MAX; lx++) {
			for (int lz = Z_MIN; lz <= Z_MAX; lz++) {
				BlockPos c = StructureBuilder.worldPos(new BlockPos(ox, 0, oz), new Structure.Block(lx, 0, lz, ""), turns);
				if (c.getX() == lectern.getX() && c.getZ() == lectern.getZ()) return Integer.MIN_VALUE;
				if (!level.hasChunkAt(c)) return Integer.MIN_VALUE;
				int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, c.getX(), c.getZ());
				BlockPos ground = new BlockPos(c.getX(), h - 1, c.getZ());
				BlockState g = level.getBlockState(ground);
				if (!g.getFluidState().isEmpty() || g.is(BlockTags.LEAVES) || g.is(BlockTags.LOGS)
						|| !g.isFaceSturdy(level, ground, Direction.UP)) {
					return Integer.MIN_VALUE;
				}
				min = Math.min(min, h);
				max = Math.max(max, h);
			}
		}
		if (max - min > 1 || Math.abs(max - lectern.getY()) > 1) return Integer.MIN_VALUE;
		return max;
	}
}
