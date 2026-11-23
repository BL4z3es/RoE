package io.github.bl4z3es.riseofempires.mc.entity;

import io.github.bl4z3es.riseofempires.RiseOfEmpires;
import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import io.github.bl4z3es.riseofempires.core.sim.Survivors;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import io.github.bl4z3es.riseofempires.mc.world.WorldBlocks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps one body in the world for each person whose settlement is loaded (tasks M1-07 and M1-11).
 *
 * <p>It starts the first survivors on their way, walks them in from beyond sight, lets them join the
 * camp when they arrive, and gives residents who have no body one, but only where no player can see
 * it appear (law L1).
 */
public final class CharacterBodies implements RoeServer.Module {
	public static final CharacterBodies INSTANCE = new CharacterBodies();

	/** Distance from the camp centre at which a walking survivor counts as arrived. */
	public static final double ARRIVED_BLOCKS = 6.0;
	/** How far from a player a new body must be. */
	private static final int HIDDEN_RADIUS = Balance.EMBODIED_RADIUS_BLOCKS;
	/** Players farther than this are not checked for line of sight. PROPOSED: not in Balance yet. */
	private static final double SIGHT_LIMIT_BLOCKS = 128.0;
	/** Extra search distance around a claim when looking for bodies. */
	private static final int SEARCH_MARGIN_BLOCKS = 128;
	/** Angles tried per second when looking for a hidden spot. */
	private static final int SPOT_ATTEMPTS = 12;
	/**
	 * When no hidden spot is found at the normal distance (open ground, a player in camp), the search
	 * moves out by this much, ring by ring, until it is past {@link #SIGHT_LIMIT_BLOCKS}. PROPOSED.
	 */
	private static final int ARRIVAL_RING_STEP_BLOCKS = 8;
	/** A walk this many times longer than expected is given up if nobody is watching. PROPOSED. */
	private static final int STUCK_FACTOR = 3;
	public static final String ASKS_TO_STAY_KEY = "riseofempires.survivor.asks_to_stay";

	private final Map<Long, Long> setOutTimes = new HashMap<>();
	/** How far from the camp a survivor starts. Only tests change it. */
	private volatile int originBlocks = Balance.SURVIVOR_ORIGIN_DISTANCE_BLOCKS;

	private CharacterBodies() {}

	/** Test hook: starts survivors this far from the camp instead of the normal distance. */
	public void setOriginDistance(int blocks) { originBlocks = blocks; }

	/** Test hook: back to {@code Balance.SURVIVOR_ORIGIN_DISTANCE_BLOCKS}. */
	public void resetOriginDistance() { originBlocks = Balance.SURVIVOR_ORIGIN_DISTANCE_BLOCKS; }

	public static void register() {
		RoeEntities.register();
		RoeServer.addModule(INSTANCE);
	}

	@Override
	public void tick(RoeServer roe, long now) {
		SimState state = roe.state();
		for (Settlement st : new ArrayList<>(state.settlements())) {
			setOut(roe, state, st, now);
			ServerLevel level = roe.level(st.centre());
			boolean embodied = roe.isEmbodied(st.centre());
			Map<Long, CharacterEntity> bodies = embodied ? findBodies(level, st) : Map.of();
			survivors(roe, state, st, level, embodied, bodies, now);
			if (embodied) residents(state, st, level, bodies);
		}
	}

	// ---- the first survivors set out

	private void setOut(RoeServer roe, SimState state, Settlement st, long now) {
		if (state.fact("survivors." + st.id()).isPresent()) return;
		long founded = state.ledger().get(st.foundedEventId()).map(Event::time).orElse(now);
		if (now < Survivors.setOutTime(founded)) return;
		List<Survivors.Template> pool = roe.content().survivors();
		if (pool.size() < Balance.STARTING_SURVIVORS) {
			RiseOfEmpires.LOGGER.warn("Not enough survivors in the data pack ({}), none will set out", pool.size());
			return;
		}
		Survivors.setOut(state, st.id(), now, pool);
	}

	// ---- walking in

	private void survivors(RoeServer roe, SimState state, Settlement st, ServerLevel level, boolean embodied,
			Map<Long, CharacterEntity> bodies, long now) {
		BlockPos centre = RoeServer.pos(st.centre());
		for (long id : Survivors.pending(state, st.id())) {
			long since = setOutTime(state, id, now);
			long walk = (long) originBlocks * Balance.WALK_TICKS_PER_BLOCK;
			CharacterEntity body = bodies.get(id);
			if (!embodied) {
				if (now - since >= walk) Survivors.join(state, id, st.id(), now, st.centre());
				continue;
			}
			if (body == null) {
				if (now - since >= walk * STUCK_FACTOR) {
					Survivors.join(state, id, st.id(), now, st.centre());
				} else {
					spawnArrival(level, st, id, centre);
				}
				continue;
			}
			if (body.job() == null) body.setJob(new ArriveJob(centre));
			if (horizontalDistance(body, centre) <= ARRIVED_BLOCKS) {
				arrive(roe, state, st, level, body, now);
			} else if (now - since >= walk * STUCK_FACTOR && !seen(level, body.blockPosition())) {
				body.discard();
				Survivors.join(state, id, st.id(), now, st.centre());
			}
		}
	}

	private long setOutTime(SimState state, long characterId, long fallback) {
		return setOutTimes.computeIfAbsent(characterId, id -> {
			String me = Long.toString(id);
			List<Event> all = state.ledger().all();
			for (int i = all.size() - 1; i >= 0; i--) {
				Event e = all.get(i);
				if (e.type().equals(EventTypes.SURVIVORS_SET_OUT)
						&& e.actors().stream().anyMatch(a -> a.kind().equals(Ref.CHARACTER) && a.id().equals(me))) {
					return e.time();
				}
			}
			return fallback;
		});
	}

	/**
	 * Puts a walking survivor's body down out of every player's sight: first at the normal distance, then
	 * farther out ring by ring. The last ring is past the sight limit, so open ground with a player
	 * watching from camp still gets its survivors, walking in from farther away.
	 */
	private void spawnArrival(ServerLevel level, Settlement st, long id, BlockPos centre) {
		int last = (int) SIGHT_LIMIT_BLOCKS + ARRIVAL_RING_STEP_BLOCKS; // the first ring past the sight limit
		for (int ring = originBlocks; ring <= Math.max(originBlocks, last); ring += ARRIVAL_RING_STEP_BLOCKS) {
			for (int i = 0; i < SPOT_ATTEMPTS; i++) {
				double angle = level.getRandom().nextDouble() * Math.PI * 2;
				int x = centre.getX() + (int) Math.round(Math.cos(angle) * ring);
				int z = centre.getZ() + (int) Math.round(Math.sin(angle) * ring);
				if (!level.isPositionEntityTicking(new BlockPos(x, centre.getY(), z))) continue;
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				BlockPos spot = new BlockPos(x, y, z);
				if (!level.getFluidState(spot).isEmpty() || !level.getFluidState(spot.below()).isEmpty()) continue;
				if (hiddenFromEveryone(level, spot)) {
					CharacterEntity.spawn(level, id, spot);
					return;
				}
			}
		}
	}

	private void arrive(RoeServer roe, SimState state, Settlement st, ServerLevel level, CharacterEntity body, long now) {
		long id = body.characterId();
		Survivors.join(state, id, st.id(), now, RoeServer.place(level, body.blockPosition()));
		body.stopWalking();
		body.setJob(null);
		Player nearest = level.getNearestPlayer(body, 64.0);
		if (nearest != null) body.getLookControl().setLookAt(nearest);
		String name = state.character(id).map(Character::name).orElse("?");
		ServerPlayer founder = null;
		try {
			founder = roe.server().getPlayerList().getPlayer(UUID.fromString(st.founder()));
		} catch (IllegalArgumentException ignored) {
			// a founder that is not a player UUID has nobody to hear them
		}
		if (founder != null) founder.sendSystemMessage(Component.translatable(ASKS_TO_STAY_KEY, name));
	}

	/** Walks to the camp centre. Reassigned by the module if the body is reloaded without it. */
	private record ArriveJob(BlockPos centre) implements CharacterEntity.Job {
		@Override
		public void tick(CharacterEntity body) { body.walkTo(centre, 3.0); }
	}

	// ---- residents get bodies

	private void residents(SimState state, Settlement st, ServerLevel level, Map<Long, CharacterEntity> bodies) {
		BlockPos centre = RoeServer.pos(st.centre());
		for (Character c : state.residents(st.id())) {
			if (c.condition().isGone() || bodies.containsKey(c.id())) continue;
			if (WorldBlocks.playerNear(level, centre)) return;
			BlockPos spot = standingSpot(level, centre);
			if (seen(level, spot)) return;
			CharacterEntity body = CharacterEntity.spawn(level, c.id(), spot);
			if (body != null) bodies.put(c.id(), body);
		}
	}

	private static BlockPos standingSpot(ServerLevel level, BlockPos centre) {
		if (level.getBlockState(centre).getCollisionShape(level, centre).isEmpty()
				&& level.getBlockState(centre.above()).getCollisionShape(level, centre.above()).isEmpty()) {
			return centre;
		}
		return new BlockPos(centre.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre.getX(), centre.getZ()), centre.getZ());
	}

	// ---- finding bodies

	/** Bodies near the settlement by character id. A second body for the same person is discarded. */
	private static Map<Long, CharacterEntity> findBodies(ServerLevel level, Settlement st) {
		BlockPos c = RoeServer.pos(st.centre());
		int r = st.claimRadius() + SEARCH_MARGIN_BLOCKS;
		AABB box = new AABB(c.getX() - r, level.getMinY(), c.getZ() - r, c.getX() + r, level.getMaxY(), c.getZ() + r);
		Map<Long, CharacterEntity> out = new HashMap<>();
		for (CharacterEntity body : level.getEntitiesOfClass(CharacterEntity.class, box)) {
			CharacterEntity first = out.putIfAbsent(body.characterId(), body);
			if (first != null) body.discard();
		}
		return out;
	}

	// ---- sight

	private static double horizontalDistance(CharacterEntity body, BlockPos centre) {
		double dx = body.getX() - (centre.getX() + 0.5), dz = body.getZ() - (centre.getZ() + 0.5);
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Farther than the hidden radius from every player, and in no player's line of sight. */
	private static boolean hiddenFromEveryone(ServerLevel level, BlockPos spot) {
		return !WorldBlocks.playerNear(level, spot) && !seen(level, spot);
	}

	/** True if any player has a clear line from their eyes to a body standing at the spot. */
	public static boolean seen(ServerLevel level, BlockPos spot) {
		Vec3[] points = {Vec3.atBottomCenterOf(spot).add(0, 0.2, 0), Vec3.atBottomCenterOf(spot).add(0, 1.6, 0)};
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator() || p.distanceToSqr(Vec3.atCenterOf(spot)) > SIGHT_LIMIT_BLOCKS * SIGHT_LIMIT_BLOCKS) continue;
			for (Vec3 point : points) {
				HitResult hit = level.clip(new ClipContext(p.getEyePosition(), point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
				if (hit.getType() == HitResult.Type.MISS) return true;
			}
		}
		return false;
	}

	/** Used by tests: the body for a character near a settlement, if one exists. */
	public static Optional<CharacterEntity> bodyOf(ServerLevel level, Settlement st, long characterId) {
		return Optional.ofNullable(findBodies(level, st).get(characterId));
	}
}
