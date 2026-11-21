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
import java.util.Optional;
import java.util.SplittableRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The Hollow Ruin (task M1-09, guards not yet): a broken old-empire watch-house near spawn holding the
 * Charter. Its place is chosen once from the world seed and written to the Ledger, then built when its
 * chunks are loaded and no player is near enough to watch it go up. Chunks are never force-loaded.
 */
public final class HollowRuin implements RoeServer.Module {
	/** Name of the structure file. */
	public static final String STRUCTURE = "hollow_ruin";
	/** Salt for the dice that choose where the ruin goes. */
	private static final long DICE_SALT = 101;
	/** PROPOSED: not in Balance yet. How far the spot moves outward when it falls on water. */
	public static final int WATER_SHIFT_BLOCKS = 24;
	/** PROPOSED: not in Balance yet. Chunk reach of the footprint around the centre: west/east and north/south-plus-path. */
	private static final int REACH_X = 7;
	private static final int REACH_NORTH = 7;
	private static final int REACH_SOUTH = 13;
	/** PROPOSED: not in Balance yet. Starter loot beside the Charter. */
	public static final int LOOT_BREAD = 3;
	public static final int LOOT_WHEAT_SEEDS = 5;

	@Override
	public void tick(RoeServer roe, long now) {
		SimState state = roe.state();
		ServerLevel level = roe.overworld();
		if (state.fact("ruin.place").isEmpty()) {
			choose(state, level, now);
		}
		if (state.fact("ruin.built").isPresent()) return;
		int[] xz = place(state);
		if (xz == null) return;
		BlockPos centre = new BlockPos(xz[0], level.getSeaLevel(), xz[1]);
		if (!level.hasChunksAt(xz[0] - REACH_X, level.getMinY(), xz[1] - REACH_NORTH, xz[0] + REACH_X, level.getMaxY(), xz[1] + REACH_SOUTH)) return;
		if (WorldBlocks.playerNear(level, centre)) return;

		int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, xz[0], xz[1]);
		if (!level.getFluidState(new BlockPos(xz[0], surface - 1, xz[1])).isEmpty()) {
			moveOut(state, level, now, xz);
			return;
		}
		Optional<Structure> ruin = StructureFiles.load(roe.server().getResourceManager(), STRUCTURE);
		if (ruin.isEmpty()) return;
		build(level, new BlockPos(xz[0], surface, xz[1]), ruin.get());
		Event placed = state.ledger().get(Long.parseLong(state.fact("ruin.event").orElseThrow())).orElseThrow();
		state.putFact("ruin.built", Integer.toString(surface), placed);
		RiseOfEmpires.LOGGER.info("The Hollow Ruin stands at {}, {}", xz[0], xz[1]);
	}

	private static int[] place(SimState state) {
		String v = state.fact("ruin.place").orElse(null);
		if (v == null) return null;
		String[] p = v.split(",");
		return new int[] {Integer.parseInt(p[0]), Integer.parseInt(p[1])};
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

	/** The spot is under water: try again further out along the same line from spawn. */
	private static void moveOut(SimState state, ServerLevel level, long now, int[] xz) {
		BlockPos spawn = level.getRespawnData().globalPos().pos();
		double dx = xz[0] - spawn.getX();
		double dz = xz[1] - spawn.getZ();
		double len = Math.max(1.0, Math.hypot(dx, dz));
		int x = xz[0] + (int) Math.round(dx / len * WATER_SHIFT_BLOCKS);
		int z = xz[1] + (int) Math.round(dz / len * WATER_SHIFT_BLOCKS);
		writePlace(state, level, now, x, z, state.fact("ruin.event").map(Long::parseLong));
	}

	private static void writePlace(SimState state, ServerLevel level, long now, int x, int z, Optional<Long> because) {
		EventDraft d = EventDraft.of(EventTypes.RUIN_PLACED, now)
				.at(RoeServer.place(level, new BlockPos(x, level.getSeaLevel(), z)))
				.actor(Ref.world())
				.effect(Ref.world(), "x", Integer.toString(x))
				.effect(Ref.world(), "z", Integer.toString(z));
		because.ifPresent(d::cause);
		Event e = state.ledger().append(d);
		state.putFact("ruin.place", x + "," + z, e);
		state.putFact("ruin.event", Long.toString(e.id()), e);
	}

	/** Places the ruin with its floor line at {@code origin} and fills its chest. */
	public static void build(ServerLevel level, BlockPos origin, Structure ruin) {
		StructureBuilder.placeAll(level, origin, ruin, 0);
		for (Structure.Block b : ruin.blocks()) {
			if (b.state().startsWith("minecraft:chest")) {
				fillChest(level, StructureBuilder.worldPos(origin, b, 0));
			}
		}
	}

	/** The Charter, plus a little to start on: bread, seeds and a stone axe. */
	static void fillChest(ServerLevel level, BlockPos pos) {
		if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) return;
		chest.setItem(4, new ItemStack(Items.WHEAT_SEEDS, LOOT_WHEAT_SEEDS));
		chest.setItem(11, new ItemStack(Items.BREAD, LOOT_BREAD));
		chest.setItem(13, new ItemStack(CharterItem.ITEM));
		chest.setItem(16, new ItemStack(Items.STONE_AXE));
		chest.setChanged();
	}
}
