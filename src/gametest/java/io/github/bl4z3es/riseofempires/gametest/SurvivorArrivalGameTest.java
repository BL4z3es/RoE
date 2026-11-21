package io.github.bl4z3es.riseofempires.gametest;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import io.github.bl4z3es.riseofempires.core.sim.Founding;
import io.github.bl4z3es.riseofempires.core.sim.Survivors;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import io.github.bl4z3es.riseofempires.mc.entity.CharacterBodies;
import io.github.bl4z3es.riseofempires.mc.entity.CharacterEntity;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Task M1-11: survivors walk in from out of view, and the arrival has a cause in the Ledger. */
public final class SurvivorArrivalGameTest {
	private static final int ORIGIN_BLOCKS = 24;
	/** Far enough that no start point on the ring is within 48 blocks of the hidden player. */
	private static final int FAR_BLOCKS = ORIGIN_BLOCKS + Balance.EMBODIED_RADIUS_BLOCKS + 8;
	private static final int HALF = ORIGIN_BLOCKS + 4;

	private static BlockPos prepare(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// Morning, so night hours do not send the new residents to bed.
		RoeServer roe = RoeServer.get().orElseThrow();
		helper.setTime(Math.floorDiv(roe.now(), 24_000L) * 24_000L + 1_000);
		BlockPos base = helper.absolutePos(new BlockPos(0, 0, 0));
		// The empty test structure has walls that block paths: clear the area and lay a floor.
		for (int x = base.getX() - HALF; x <= base.getX() + HALF; x++) {
			for (int z = base.getZ() - HALF; z <= base.getZ() + HALF; z++) {
				for (int dy = 1; dy <= 6; dy++) level.setBlockAndUpdate(new BlockPos(x, base.getY() + dy, z), Blocks.AIR.defaultBlockState());
				level.setBlockAndUpdate(new BlockPos(x, base.getY(), z), Blocks.STONE.defaultBlockState());
			}
		}
		// Keep every chunk of the walk entity-ticking, as a nearby player would in play.
		for (int cx = (base.getX() - HALF) >> 4; cx <= (base.getX() + HALF) >> 4; cx++) {
			for (int cz = (base.getZ() - HALF) >> 4; cz <= (base.getZ() + HALF) >> 4; cz++) level.setChunkForced(cx, cz, true);
		}
		return base.above();
	}

	private static void releaseChunks(ServerLevel level, BlockPos centre) {
		for (int cx = (centre.getX() - HALF) >> 4; cx <= (centre.getX() + HALF) >> 4; cx++) {
			for (int cz = (centre.getZ() - HALF) >> 4; cz <= (centre.getZ() + HALF) >> 4; cz++) level.setChunkForced(cx, cz, false);
		}
	}

	private static long found(SimState s, ServerLevel level, BlockPos centre, long now) {
		String player = UUID.randomUUID().toString();
		Place place = RoeServer.place(level, centre);
		Founding.found(s, player, "Arrivalhold", place, now).orElseThrow();
		long sid = s.settlementFoundedBy(player).orElseThrow();
		Survivors.setOut(s, sid, now, List.of(
				new Survivors.Template("Ada", "steady", "a roof", "the fire at Elmford"),
				new Survivors.Template("Bram", "quick", "his brother", "the long road"),
				new Survivors.Template("Cora", "proud", "land", "the old empire")));
		return sid;
	}

	private static ServerPlayer hiddenPlayer(GameTestHelper helper, ServerLevel level, BlockPos centre) {
		BlockPos box = centre.east(FAR_BLOCKS);
		level.getChunk(box.getX() >> 4, box.getZ() >> 4);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int dy = -1; dy <= 4; dy++) {
					boolean shell = Math.abs(dx) == 2 || Math.abs(dz) == 2 || dy == -1 || dy == 4;
					level.setBlockAndUpdate(box.offset(dx, dy, dz), shell ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
				}
			}
		}
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.snapTo(box.getX() + 0.5, box.getY(), box.getZ() + 0.5);
		return player;
	}

	private static double distance(Entity e, BlockPos centre) {
		return Math.hypot(e.getX() - (centre.getX() + 0.5), e.getZ() - (centre.getZ() + 0.5));
	}

	private static List<CharacterEntity> bodies(ServerLevel level, BlockPos centre, List<Long> ids) {
		return level.getEntitiesOfClass(CharacterEntity.class, new AABB(centre).inflate(HALF + 20))
				.stream().filter(b -> ids.contains(b.characterId())).toList();
	}


	/**
	 * Stage 0: survivors start {@code ORIGIN_BLOCKS} away and walk in; their arrival has a cause in the Ledger.
	 * Stage 1: a second camp's survivors set out while a player stands at the camp; none may appear within
	 * 48 blocks of that player. (One test, because a player's 48-block radius would also block another
	 * test's survivors, and with no player at all the area is not entity-ticking.)
	 */
	@GameTest(maxTicks = 2400, padding = 40)
	public void survivorsWalkInFromBeyondSight(GameTestHelper helper) {
		RoeServer roe = RoeServer.get().orElseThrow();
		SimState s = roe.state();
		ServerLevel level = helper.getLevel();
		BlockPos centre = prepare(helper);
		// Other tests move the world clock, which would make walkers 'arrive' by time alone. This test keeps its own
		// clock, far ahead of the real one, so only the walk can bring the survivors in.
		long[] virtualNow = {roe.now() + 1_000_000L};
		long sid = found(s, level, centre, virtualNow[0]);
		List<Long> ids = Survivors.pending(s, sid);
		helper.assertTrue(ids.size() == Balance.STARTING_SURVIVORS, "expected the starting survivors, got " + ids.size());
		CharacterBodies.INSTANCE.setOriginDistance(ORIGIN_BLOCKS);

		int[] stage = {0};
		int[] ticks = {0};
		// A player far away, in a closed stone box, keeps the camp's chunks entity-ticking but sees nothing.
		ServerPlayer[] player = {hiddenPlayer(helper, level, centre)};
		long[] second = {0};
		List<Long> secondIds = new java.util.ArrayList<>();
		Map<Long, Double> firstSeen = new HashMap<>();
		Map<Long, Double> lastSeen = new HashMap<>();
		helper.succeedWhen(() -> {
			CharacterBodies.INSTANCE.tick(roe, ++virtualNow[0]);
			if (stage[0] == 1) {
				helper.assertTrue(bodies(level, centre, secondIds).isEmpty(), "a survivor appeared within 48 blocks of a player");
				if (ticks[0]++ < 100) helper.assertTrue(false, "watching with a player present");
				level.getServer().getPlayerList().remove(player[0]);
				CharacterBodies.INSTANCE.resetOriginDistance();
				bodies(level, centre, secondIds).forEach(Entity::discard);
				releaseChunks(level, centre);
				return;
			}
			for (CharacterEntity body : bodies(level, centre, ids)) {
				double d = distance(body, centre);
				firstSeen.putIfAbsent(body.characterId(), d);
				lastSeen.put(body.characterId(), d);
			}
			helper.assertTrue(Survivors.pending(s, sid).isEmpty(), "survivors still walking: " + Survivors.pending(s, sid)
					+ " first=" + firstSeen + " last=" + lastSeen);
			Set<Long> bodyIds = new HashSet<>();
			for (CharacterEntity body : bodies(level, centre, ids)) {
				helper.assertTrue(bodyIds.add(body.characterId()), "two bodies share a character id");
			}
			for (long id : ids) {
				helper.assertTrue(firstSeen.containsKey(id), "survivor " + id + " never had a body");
				helper.assertTrue(firstSeen.get(id) >= ORIGIN_BLOCKS - 2, "survivor " + id + " appeared only " + firstSeen.get(id) + " blocks away");
				helper.assertTrue(lastSeen.get(id) < firstSeen.get(id) - 10, "survivor " + id + " did not walk in: " + firstSeen.get(id) + " -> " + lastSeen.get(id));
				Event joined = null;
				for (Event e : s.ledger().all()) {
					if (e.type().equals(EventTypes.SURVIVOR_JOINED) && e.actors().stream().anyMatch(a -> a.id().equals(Long.toString(id)))) joined = e;
				}
				helper.assertTrue(joined != null, "no SURVIVOR_JOINED event for " + id);
				List<Event> chain = s.ledger().causeChain(joined.id());
				helper.assertTrue(chain.stream().anyMatch(e -> e.type().equals(EventTypes.SURVIVORS_SET_OUT)), "no set-out in the cause chain");
				helper.assertTrue(chain.stream().anyMatch(e -> e.type().equals(EventTypes.SETTLEMENT_FOUNDED)), "no founding in the cause chain");
			}
			bodies(level, centre, ids).forEach(Entity::discard);

			// Stage 1: a player at the camp, and a second camp's survivors set out.
			second[0] = found(s, level, centre, virtualNow[0]);
			secondIds.addAll(Survivors.pending(s, second[0]));
			player[0].snapTo(centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5);
			stage[0] = 1;
			helper.assertTrue(false, "stage 1: a player is present; watching for survivors");
		});
	}
}
