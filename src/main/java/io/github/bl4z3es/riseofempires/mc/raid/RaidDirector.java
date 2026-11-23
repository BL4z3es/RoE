package io.github.bl4z3es.riseofempires.mc.raid;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Raid;
import io.github.bl4z3es.riseofempires.core.model.RaidStage;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.sim.Raids;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import io.github.bl4z3es.riseofempires.mc.entity.CharacterEntity;
import io.github.bl4z3es.riseofempires.mc.stockpile.GranaryStore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Gives a raid its bodies (embodied half of task M1-20). The core plans the raid and writes the warning
 * and the arrival; this module sends the raiders in from beyond sight, lets them take food from the real
 * chests, reports kills and thefts to the core, and sends them home. A raid whose settlement nobody is
 * near is settled by the core alone ({@link Raids#resolveUnwatched}).
 */
public final class RaidDirector implements RoeServer.Module {
	/** PROPOSED: not in Balance yet. Ticks a raider must stand at the granary before the raiders take the food. */
	public static final int STEAL_TICKS = 100;
	/** PROPOSED: not in Balance yet. How much farther out a blocked spawn spot may move, in blocks. */
	public static final int SPAWN_EXTRA_BLOCKS = 64;
	private static final int SPAWN_STEP_BLOCKS = 8;
	private static final double EYE_HEIGHT = 1.6;

	private final Set<Long> spawned = new HashSet<>();
	private RoeServer lastRoe;

	/** Registers the entity, the tracking of loaded raiders and both modules. Called once from {@link io.github.bl4z3es.riseofempires.RiseOfEmpires}. */
	public static void register() {
		RaidEntities.register();
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof RaiderEntity r) RaiderEntity.LIVE.add(r);
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof RaiderEntity r) RaiderEntity.LIVE.remove(r);
		});
		RoeServer.addModule(new RaidDirector());
		RoeServer.addModule(new RuinGuards());
	}

	// ---- kills

	static void raiderDied(RaiderEntity raider, DamageSource source) {
		RoeServer roe = RoeServer.get().orElse(null);
		if (roe == null) return;
		Raid raid = roe.state().raid(raider.raidId()).orElse(null);
		if (raid == null || raid.stage() == RaidStage.ENDED || raid.stage() == RaidStage.PLANNED) return;
		Entity killer = source.getEntity();
		Ref ref = killer instanceof ServerPlayer p ? Ref.player(p.getStringUUID())
				: killer instanceof CharacterEntity c ? Ref.character(c.characterId()) : Ref.world();
		Raids.raiderKilled(roe.state(), raid.id(), ref, roe.now());
	}

	// ---- each second

	@Override
	public void tick(RoeServer roe, long now) {
		if (roe != lastRoe) {
			lastRoe = roe;
			spawned.clear();
		}
		for (RaiderEntity r : RaiderEntity.LIVE) if (r.raidId() != 0) spawned.add(r.raidId());
		for (Raid raid : new ArrayList<>(roe.state().raids())) {
			Settlement st = roe.state().settlement(raid.settlementId()).orElse(null);
			if (st == null) continue;
			if (raid.stage() == RaidStage.ENDED) {
				retreatAll(roe, raid, living(raid.id()));
				continue;
			}
			if (raid.stage() == RaidStage.PLANNED) continue;
			boolean embodied = roe.isEmbodied(st.centre());
			List<RaiderEntity> raiders = living(raid.id());
			if (embodied && raiders.isEmpty() && !spawned.contains(raid.id())) trySpawn(roe, raid, st, now);
			raiders = living(raid.id());
			BlockPos goal = goal(st);
			for (RaiderEntity r : raiders) {
				if (r.order() == RaiderEntity.Order.NONE) r.giveOrder(RaiderEntity.Order.ASSAULT, goal);
			}
			if (raid.stage() == RaidStage.ARRIVED) {
				if (!embodied && raiders.isEmpty()) {
					Raids.resolveUnwatched(roe.state(), raid.id(), roe.content().food()::isFood, now);
				} else {
					stealIfReady(roe, raid, st, raiders, now);
				}
			}
		}
	}

	private static List<RaiderEntity> living(long raidId) {
		List<RaiderEntity> out = new ArrayList<>();
		for (RaiderEntity r : RaiderEntity.LIVE) if (r.raidId() == raidId && r.isAlive()) out.add(r);
		return out;
	}

	private static BlockPos goal(Settlement st) {
		return RoeServer.pos(st.granary() != null ? st.granary() : st.centre());
	}

	private static void retreatAll(RoeServer roe, Raid raid, List<RaiderEntity> raiders) {
		BlockPos home = RoeServer.pos(raid.origin());
		for (RaiderEntity r : raiders) r.giveOrder(RaiderEntity.Order.RETREAT, home);
	}

	// ---- stealing

	private void stealIfReady(RoeServer roe, Raid raid, Settlement st, List<RaiderEntity> raiders, long now) {
		boolean ready = false;
		for (RaiderEntity r : raiders) if (r.ticksAtTarget() >= STEAL_TICKS) ready = true;
		if (!ready) return;
		Map<String, Integer> taken = new TreeMap<>();
		if (st.granary() != null) {
			ServerLevel level = roe.level(st.granary());
			GranaryStore store = new GranaryStore(level, RoeServer.pos(st.granary()));
			Map<String, Integer> remove = new TreeMap<>();
			store.count().forEach((item, n) -> {
				if (!roe.content().food().isFood(item)) return;
				int take = (int) Math.floor(n * Balance.RAID_STEAL_FRACTION);
				if (take > 0) {
					taken.put(item, take);
					remove.put(item, -take);
				}
			});
			if (!remove.isEmpty() && !store.apply(remove)) return;
		}
		Raids.stolen(roe.state(), raid.id(), taken, now);
		retreatAll(roe, raid, raiders);
	}

	// ---- spawning

	private void trySpawn(RoeServer roe, Raid raid, Settlement st, long now) {
		ServerLevel level = roe.level(st.centre());
		BlockPos origin = RoeServer.pos(raid.origin());
		BlockPos goal = goal(st);
		double travelTicks = Math.hypot(origin.getX() - goal.getX(), origin.getZ() - goal.getZ()) * Balance.WALK_TICKS_PER_BLOCK;
		if (now < raid.arriveTime() - travelTicks) return;
		BlockPos spot = findSpawn(level, origin, goal);
		if (spot == null) return; // wait a second and try again
		spawned.add(raid.id());
		for (int i = 0; i < raid.raidersLeft(); i++) {
			int x = spot.getX() + (i % 3 - 1) * 2;
			int z = spot.getZ() + (i / 3) * 2;
			BlockPos p = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
			RaiderEntity r = RaiderEntity.spawn(level, raid.id(), p);
			if (r != null) r.giveOrder(RaiderEntity.Order.ASSAULT, goal);
		}
	}

	/** A surface spot at the origin, or farther out along the same line, that no player can see or is near. */
	private static BlockPos findSpawn(ServerLevel level, BlockPos origin, BlockPos goal) {
		double dx = origin.getX() - goal.getX();
		double dz = origin.getZ() - goal.getZ();
		double len = Math.max(1.0, Math.hypot(dx, dz));
		for (int extra = 0; extra <= SPAWN_EXTRA_BLOCKS; extra += SPAWN_STEP_BLOCKS) {
			int x = origin.getX() + (int) Math.round(dx / len * extra);
			int z = origin.getZ() + (int) Math.round(dz / len * extra);
			BlockPos probe = new BlockPos(x, origin.getY(), z);
			if (!level.isPositionEntityTicking(probe)) continue;
			BlockPos p = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
			if (!level.getBlockState(p.below()).isSolid() || !level.getFluidState(p.below()).isEmpty()) continue;
			if (hiddenFromEveryPlayer(level, p)) return p;
		}
		return null;
	}

	/** True if every player is farther than the Embodied radius and has no clear line to the spot. */
	static boolean hiddenFromEveryPlayer(ServerLevel level, BlockPos p) {
		Vec3 spot = new Vec3(p.getX() + 0.5, p.getY() + EYE_HEIGHT, p.getZ() + 0.5);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(spot) <= (double) Balance.EMBODIED_RADIUS_BLOCKS * Balance.EMBODIED_RADIUS_BLOCKS) return false;
			HitResult hit = level.clip(new ClipContext(player.getEyePosition(), spot, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			if (hit.getType() == HitResult.Type.MISS) return false;
		}
		return true;
	}
}
