package io.github.bl4z3es.riseofempires.mc.opening;

import io.github.bl4z3es.riseofempires.RiseOfEmpires;
import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import io.github.bl4z3es.riseofempires.core.sim.Dice;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import io.github.bl4z3es.riseofempires.mc.block.CharterItem;
import io.github.bl4z3es.riseofempires.mc.world.WorldBlocks;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The Hollow Ruin (task M1-09, guards not yet): a broken old-empire watch-house near spawn holding the
 * Charter.
 *
 * <p>Its place is chosen once from the world seed and written to the Ledger. Its base height is fixed once,
 * from the chunk generator's own estimate, before any of its chunks exist, and kept in the fact
 * {@code ruin.y}. Each chunk that overlaps it receives its part the moment it is generated, before any
 * client can have it (law L1). A chunk that was already loaded when the spot was chosen (an old world)
 * is filled in later, only while no player is within the Embodied radius. Chunks are never force-loaded.
 * When every chunk has its part the fact {@code ruin.built} is set.
 */
public final class HollowRuin implements RoeServer.Module {
	/** Name of the structure file. */
	public static final String STRUCTURE = "hollow_ruin";
	/** Salt for the dice that choose where the ruin goes. */
	private static final long DICE_SALT = 101;
	/** PROPOSED: not in Balance yet. How far the spot moves outward when its footprint is wet or steep. */
	public static final int WATER_SHIFT_BLOCKS = 24;
	/** PROPOSED: not in Balance yet. Most difference in ground height across the footprint. */
	public static final int MAX_FOOTPRINT_RISE = 3;
	/** PROPOSED: not in Balance yet. Blocks between ground samples across the footprint. */
	private static final int FOOTPRINT_SAMPLE_STEP = 3;
	/** PROPOSED: not in Balance yet. Degrees the search turns when it runs past the farthest allowed distance. */
	private static final int SEARCH_TURN_DEGREES = 37;
	/** PROPOSED: not in Balance yet. Spots tried before the ruin settles for one that is merely dry at its centre. */
	public static final int MAX_SITE_TRIES = 120;
	/** PROPOSED: not in Balance yet. Most spot changes in one tick before giving up until the next. */
	private static final int MAX_MOVES_PER_TICK = 40;
	/** PROPOSED: not in Balance yet. Footprint reach around the centre, west/east, north and south (with the path). */
	private static final int REACH_X = 7;
	private static final int REACH_NORTH = 7;
	private static final int REACH_SOUTH = 13;
	/** PROPOSED: not in Balance yet. Starter loot beside the Charter. */
	public static final int LOOT_BREAD = 3;
	public static final int LOOT_WHEAT_SEEDS = 5;
	/** No shape updates, no neighbour updates, no placement side effects: safe inside chunk generation. */
	private static final int QUIET = Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE;

	private Structure cached;

	/** Hooks chunk generation. Called once from {@link Opening#register()}. */
	public static void registerGeneration() {
		ServerChunkEvents.CHUNK_GENERATE.register((level, chunk) -> {
			RoeServer roe = RoeServer.get().orElse(null);
			if (roe == null || level != roe.overworld()) return;
			SimState state = roe.state();
			int[] xz = place(state);
			Integer y = baseY(state);
			if (xz == null || y == null || state.fact("ruin.built").isPresent()) return;
			if (!overlaps(xz[0], xz[1], chunk.getPos())) return;
			Structure ruin = StructureFiles.load(roe.server().getResourceManager(), STRUCTURE).orElse(null);
			if (ruin == null) return;
			partDone(state, chunk, new BlockPos(xz[0], y, xz[1]), ruin);
			finishIfComplete(state, level, new BlockPos(xz[0], y, xz[1]), ruin);
		});
	}

	@Override
	public void tick(RoeServer roe, long now) {
		SimState state = roe.state();
		ServerLevel level = roe.overworld();
		if (state.fact("ruin.place").isEmpty()) {
			choose(state, level, now);
		}
		for (int i = 0; i < MAX_MOVES_PER_TICK && state.fact("ruin.y").isEmpty(); i++) {
			settle(state, level, now);
		}
		if (state.fact("ruin.y").isEmpty()) return;
		int[] xz = place(state);
		BlockPos origin = new BlockPos(xz[0], baseY(state), xz[1]);
		if (cached == null) cached = StructureFiles.load(roe.server().getResourceManager(), STRUCTURE).orElse(null);
		if (cached == null) return;
		fillChestOnceLoaded(state, level, origin, cached);
		if (state.fact("ruin.built").isPresent()) return;

		// Fallback for chunks that existed before the spot was chosen: fill them while nobody is near.
		for (ChunkPos cp : footprintChunks(origin, cached)) {
			if (state.fact(chunkFact(cp)).isPresent() || !level.hasChunk(cp.x(), cp.z())) continue;
			if (WorldBlocks.playerNear(level, new BlockPos(cp.getMiddleBlockX(), origin.getY(), cp.getMiddleBlockZ()))) continue;
			partDone(state, level.getChunk(cp.x(), cp.z()), origin, cached);
		}
		finishIfComplete(state, level, origin, cached);
	}

	private static int[] place(SimState state) {
		String v = state.fact("ruin.place").orElse(null);
		if (v == null) return null;
		String[] p = v.split(",");
		return new int[] {Integer.parseInt(p[0]), Integer.parseInt(p[1])};
	}

	private static Integer baseY(SimState state) {
		return state.fact("ruin.y").map(Integer::parseInt).orElse(null);
	}

	private static boolean overlaps(int x, int z, ChunkPos cp) {
		return cp.getMinBlockX() <= x + REACH_X && cp.getMaxBlockX() >= x - REACH_X
				&& cp.getMinBlockZ() <= z + REACH_SOUTH && cp.getMaxBlockZ() >= z - REACH_NORTH;
	}

	/** The chunks the structure's blocks fall in, around this origin. */
	public static Set<ChunkPos> footprintChunks(BlockPos origin, Structure s) {
		Set<ChunkPos> out = new LinkedHashSet<>();
		for (Structure.Block b : s.blocks()) {
			out.add(ChunkPos.containing(StructureBuilder.worldPos(origin, b, 0)));
		}
		return out;
	}

	private static String chunkFact(ChunkPos cp) { return "ruin.chunk." + cp.x() + "," + cp.z(); }

	/** Places this chunk's part and records that it has it. */
	private static void partDone(SimState state, LevelChunk chunk, BlockPos origin, Structure ruin) {
		if (state.fact(chunkFact(chunk.getPos())).isPresent()) return;
		placePart(chunk, origin, ruin);
		Event placed = state.ledger().get(Long.parseLong(state.fact("ruin.event").orElseThrow())).orElseThrow();
		state.putFact(chunkFact(chunk.getPos()), "1", placed);
	}

	private static void finishIfComplete(SimState state, ServerLevel level, BlockPos origin, Structure ruin) {
		if (state.fact("ruin.built").isPresent()) return;
		for (ChunkPos cp : footprintChunks(origin, ruin)) {
			if (state.fact(chunkFact(cp)).isEmpty()) return;
		}
		Event placed = state.ledger().get(Long.parseLong(state.fact("ruin.event").orElseThrow())).orElseThrow();
		state.putFact("ruin.built", Integer.toString(origin.getY()), placed);
		RiseOfEmpires.LOGGER.info("The Hollow Ruin stands at {}, {}", origin.getX(), origin.getZ());
	}

	/** First choice: an angle and a distance from world spawn, from the seed. */
	private static void choose(SimState state, ServerLevel level, long now) {
		BlockPos spawn = level.getRespawnData().globalPos().pos();
		SplittableRandom dice = Dice.of(state.seed(), DICE_SALT);
		double angle = dice.nextDouble() * Math.PI * 2;
		int span = Balance.RUIN_MAX_DISTANCE_BLOCKS - Balance.RUIN_MIN_DISTANCE_BLOCKS;
		double dist = Balance.RUIN_MIN_DISTANCE_BLOCKS + dice.nextInt(span + 1);
		int x = spawn.getX() + (int) Math.round(Math.cos(angle) * dist);
		int z = spawn.getZ() + (int) Math.round(Math.sin(angle) * dist);
		writePlace(state, level, now, x, z, Optional.empty());
	}

	/**
	 * Fixes the base height from the generator's estimate once the whole footprint is dry and close to
	 * level, or moves the spot. A spot is moved outward from spawn; past the farthest allowed distance the
	 * search turns and starts again at the nearest. After {@link #MAX_SITE_TRIES} spots it accepts one
	 * whose centre is dry, so a world of ocean still gets its ruin.
	 */
	private static void settle(SimState state, ServerLevel level, long now) {
		int[] xz = place(state);
		ChunkGenerator gen = level.getChunkSource().getGenerator();
		var random = level.getChunkSource().randomState();
		int tries = state.fact("ruin.tries").map(Integer::parseInt).orElse(0);
		int h = gen.getBaseHeight(xz[0], xz[1], Heightmap.Types.WORLD_SURFACE_WG, level, random);
		boolean centreDry = gen.getBaseColumn(xz[0], xz[1], level, random).getBlock(h - 1).getFluidState().isEmpty();
		if (footprintFits(level, gen, random, xz[0], xz[1]) || (tries >= MAX_SITE_TRIES && centreDry)) {
			Event placed = state.ledger().get(Long.parseLong(state.fact("ruin.event").orElseThrow())).orElseThrow();
			state.putFact("ruin.y", Integer.toString(h), placed);
			return;
		}
		BlockPos spawn = level.getRespawnData().globalPos().pos();
		double dx = xz[0] - spawn.getX();
		double dz = xz[1] - spawn.getZ();
		double len = Math.max(1.0, Math.hypot(dx, dz));
		double angle = Math.atan2(dz, dx);
		double dist = len + WATER_SHIFT_BLOCKS;
		if (dist > Balance.RUIN_MAX_DISTANCE_BLOCKS) {
			angle += Math.toRadians(SEARCH_TURN_DEGREES);
			dist = Balance.RUIN_MIN_DISTANCE_BLOCKS;
		}
		int x = spawn.getX() + (int) Math.round(Math.cos(angle) * dist);
		int z = spawn.getZ() + (int) Math.round(Math.sin(angle) * dist);
		Event moved = writePlace(state, level, now, x, z, state.fact("ruin.event").map(Long::parseLong));
		state.putFact("ruin.tries", Integer.toString(tries + 1), moved);
	}

	/** True if no ground sample across the footprint is water and the ground rises at most {@link #MAX_FOOTPRINT_RISE}. */
	private static boolean footprintFits(ServerLevel level, ChunkGenerator gen, net.minecraft.world.level.levelgen.RandomState random, int x, int z) {
		int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
		for (int ox = -REACH_X; ox <= REACH_X; ox = next(ox, REACH_X)) {
			for (int oz = -REACH_NORTH; oz <= REACH_SOUTH; oz = next(oz, REACH_SOUTH)) {
				int h = gen.getBaseHeight(x + ox, z + oz, Heightmap.Types.WORLD_SURFACE_WG, level, random);
				if (!gen.getBaseColumn(x + ox, z + oz, level, random).getBlock(h - 1).getFluidState().isEmpty()) return false;
				min = Math.min(min, h);
				max = Math.max(max, h);
				if (max - min > MAX_FOOTPRINT_RISE) return false;
			}
		}
		return true;
	}

	/** The next sample offset: every {@link #FOOTPRINT_SAMPLE_STEP} blocks, always including the far edge. */
	private static int next(int offset, int edge) {
		if (offset == edge) return edge + 1;
		return Math.min(edge, offset + FOOTPRINT_SAMPLE_STEP);
	}

	private static Event writePlace(SimState state, ServerLevel level, long now, int x, int z, Optional<Long> because) {
		EventDraft d = EventDraft.of(EventTypes.RUIN_PLACED, now)
				.at(RoeServer.place(level, new BlockPos(x, level.getSeaLevel(), z)))
				.actor(Ref.world())
				.effect(Ref.world(), "x", Integer.toString(x))
				.effect(Ref.world(), "z", Integer.toString(z));
		because.ifPresent(d::cause);
		Event e = state.ledger().append(d);
		state.putFact("ruin.place", x + "," + z, e);
		state.putFact("ruin.event", Long.toString(e.id()), e);
		return e;
	}

	/**
	 * Places the blocks of the ruin that fall inside one chunk, straight into the chunk (no neighbour or
	 * shape updates, so nothing outside it is read), and fills the chest if it is here.
	 */
	public static void placePart(LevelChunk chunk, BlockPos origin, Structure ruin) {
		ServerLevel level = (ServerLevel) chunk.getLevel();
		ChunkPos cp = chunk.getPos();
		for (Structure.Block b : ruin.blocks()) {
			BlockPos p = StructureBuilder.worldPos(origin, b, 0);
			if (p.getX() >> 4 != cp.x() || p.getZ() >> 4 != cp.z() || p.getY() < level.getMinY() || p.getY() > level.getMaxY()) continue;
			chunk.setBlockState(p, StructureBuilder.state(level, b, 0), QUIET);
		}
		// The chest is filled on a later tick (fillChestOnceLoaded): changing a container marks its chunk
		// dirty, and asking for a chunk while it is still being generated deadlocks the server.
	}

	/** Fills the ruin's chest once its part is placed and its chunk is fully loaded. Once only. */
	private static void fillChestOnceLoaded(SimState state, ServerLevel level, BlockPos origin, Structure ruin) {
		if (state.fact("ruin.chest").isPresent()) return;
		for (Structure.Block b : ruin.blocks()) {
			if (!b.state().startsWith("minecraft:chest")) continue;
			BlockPos p = StructureBuilder.worldPos(origin, b, 0);
			if (state.fact(chunkFact(ChunkPos.containing(p))).isEmpty() || !level.isLoaded(p)) return;
			if (level.getBlockEntity(p) instanceof ChestBlockEntity chest) {
				fillChest(chest);
				Event placed = state.ledger().get(Long.parseLong(state.fact("ruin.event").orElseThrow())).orElseThrow();
				state.putFact("ruin.chest", p.getX() + "," + p.getY() + "," + p.getZ(), placed);
			}
			return;
		}
	}

	/** Places the whole ruin at once with its floor line at {@code origin} and fills its chest. For tests and tools. */
	public static void build(ServerLevel level, BlockPos origin, Structure ruin) {
		StructureBuilder.placeAll(level, origin, ruin, 0);
		for (Structure.Block b : ruin.blocks()) {
			if (b.state().startsWith("minecraft:chest")
					&& level.getBlockEntity(StructureBuilder.worldPos(origin, b, 0)) instanceof ChestBlockEntity chest) {
				fillChest(chest);
			}
		}
	}

	/** The Charter, plus a little to start on: bread, seeds and a stone axe. */
	public static void fillChest(ChestBlockEntity chest) {
		chest.setItem(4, new ItemStack(Items.WHEAT_SEEDS, LOOT_WHEAT_SEEDS));
		chest.setItem(11, new ItemStack(Items.BREAD, LOOT_BREAD));
		chest.setItem(13, new ItemStack(CharterItem.ITEM));
		chest.setItem(16, new ItemStack(Items.STONE_AXE));
		chest.setChanged();
	}
}
