package io.github.bl4z3es.riseofempires.mc.job;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.model.Blueprint;
import io.github.bl4z3es.riseofempires.core.model.BlueprintBlock;
import io.github.bl4z3es.riseofempires.core.model.BuildSite;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Profession;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.sim.Building;
import io.github.bl4z3es.riseofempires.core.sim.Stores;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import io.github.bl4z3es.riseofempires.mc.entity.CharacterEntity;
import io.github.bl4z3es.riseofempires.mc.world.WorldBlocks;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Tasks M1-13 and M1-14: builds the settlement's active blueprint, one block at a time, in view. It
 * fetches the needed item from the store, walks within reach of the spot, waits, sets the block and
 * records it. Only one builder works a site at a time, so two builders never place the same block.
 */
public final class BuilderJob extends WorkJob {
	private enum Phase { PLAN, FETCH, BUILD, TIDY }

	/**
	 * How far a body can place a block, measured from its feet: about 4.5 from the eyes, which stand 1.6
	 * above them. PROPOSED: not in Balance yet (the brief said 3; roofs need more).
	 */
	private static final double REACH = 6.0;
	/** Reach grows by one block per this many ticks of failed walking, up to {@link #MAX_REACH}. PROPOSED. */
	private static final int REACH_GROWTH_TICKS = 200;
	private static final double MAX_REACH = 8.0;
	/** Already-correct blocks counted in one step. */
	private static final int SKIP_BATCH = 64;
	/** A site's builder lock lapses after this many ticks without a visit. PROPOSED. */
	private static final int LOCK_TICKS = 100;
	private static final int MISSING_RETRY_TICKS = 100;
	/** Ticks spent walking off a block's spot before trying again. PROPOSED. */
	private static final int STEP_AWAY_TICKS = 20;

	private static final Map<Long, long[]> LOCKS = new HashMap<>();

	private Phase phase = Phase.PLAN;
	private BuildSite site;
	private Blueprint blueprint;
	private BlockPos spot;
	private BlockState wanted;
	private BlueprintBlock block;
	private String fetchItem;
	private String missingSaid = "";
	private int stepping;
	private int unreached;

	@Override
	public Profession profession() { return Profession.BUILDER; }

	@Override
	protected void work(CharacterEntity body, RoeServer roe, Settlement st, Character me) {
		ServerLevel level = (ServerLevel) body.level();
		if (depositTrip(body, roe, st, me)) return;
		Optional<BuildSite> active = Building.activeSite(roe.state(), st.id());
		if (active.isEmpty()) {
			site = null;
			phase = Phase.PLAN;
			startDeposit(body, roe, st);
			return;
		}
		if (!lock(active.get().id(), me.id(), level.getGameTime())) return;
		site = active.get();
		blueprint = roe.content().blueprints().get(site.blueprintId());
		if (blueprint == null) return;
		if (phase == Phase.PLAN) plan(body, roe, st, level, me);
		switch (phase) {
			case FETCH -> fetch(body, roe, st, level, me);
			case BUILD -> build(body, roe, level, me);
			default -> { }
		}
	}

	/** True if this person holds, or can take, the site's builder lock. */
	private static boolean lock(long siteId, long characterId, long gameTime) {
		long[] l = LOCKS.get(siteId);
		if (l == null || l[0] == characterId || gameTime - l[1] > LOCK_TICKS || l[1] > gameTime) {
			LOCKS.put(siteId, new long[] {characterId, gameTime});
			return true;
		}
		return false;
	}

	// ---- planning: count what is already right, then decide to fetch or build

	private void plan(CharacterEntity body, RoeServer roe, Settlement st, ServerLevel level, Character me) {
		int skipped = 0;
		while (skipped < SKIP_BATCH && site.placed() + skipped < site.total()) {
			BlueprintBlock b = blueprint.blocks().get(site.placed() + skipped);
			BlockPos p = RoeServer.pos(Building.worldPos(site, b));
			if (!level.isLoaded(p) || !correct(level, p, WorldBlocks.parse(level, b.blockState(), site.rotation()))) break;
			skipped++;
		}
		if (skipped > 0) {
			Building.blocksBuilt(roe.state(), site.id(), skipped, me.id(), roe.now(), roe.content().blueprints());
			return;
		}
		block = blueprint.blocks().get(site.placed());
		spot = RoeServer.pos(Building.worldPos(site, block));
		if (!level.isLoaded(spot)) return;
		wanted = WorldBlocks.parse(level, block.blockState(), site.rotation());
		boolean costs = !block.item().isEmpty() && !wanted.isAir();
		if (costs && carried(body, block.item()) == 0) {
			fetchItem = block.item();
			phase = Phase.FETCH;
			resetWalk();
			resetWait();
		} else {
			phase = Phase.BUILD;
			resetWalk();
			resetWait();
		}
	}

	private static boolean correct(ServerLevel level, BlockPos p, BlockState target) {
		BlockState now = level.getBlockState(p);
		return target.isAir() ? now.isAir() : now.is(target.getBlock());
	}

	private static int carried(CharacterEntity body, String item) { return body.carried().getOrDefault(item, 0); }

	// ---- fetching from the store

	private void fetch(CharacterEntity body, RoeServer roe, Settlement st, ServerLevel level, Character me) {
		if (waitingForRetry(level)) return;
		Optional<JobStore> store = JobStore.of(roe, st);
		if (store.isEmpty()) {
			missing(body, "message.riseofempires.builder.no_store", st.name());
			retryAfter(level, MISSING_RETRY_TICKS);
			return;
		}
		JobStore.Chest chest = store.get().nearest(body.position());
		if (!walk(body, chest.pos(), CHEST_REACH)) {
			if (gaveUp()) {
				resetWalk();
				retryAfter(level, MISSING_RETRY_TICKS);
			}
			return;
		}
		if (!waitFor(body, Balance.TAKE_TICKS)) return;
		Optional<Item> item = item(fetchItem);
		int need = Math.min(Balance.CARRY_CAPACITY, remainingNeed(fetchItem));
		int taken = 0;
		if (item.isPresent()) {
			for (JobStore.Chest c : store.get().chests()) {
				if (taken >= need) break;
				taken += body.takeFrom(c.container(), item.get(), need - taken);
			}
		}
		if (taken > 0) {
			Stores.take(roe.state(), st.id(), me.id(), Map.of(fetchItem, taken), roe.now());
			missingSaid = "";
			phase = Phase.BUILD;
			resetWalk();
		} else {
			missing(body, "message.riseofempires.builder.missing", fetchItem);
			retryAfter(level, MISSING_RETRY_TICKS);
		}
	}

	private int remainingNeed(String itemId) {
		int n = 0;
		for (int i = site.placed(); i < site.total(); i++) {
			BlueprintBlock b = blueprint.blocks().get(i);
			if (b.item().equals(itemId) && !b.blockState().startsWith("minecraft:air")) n++;
		}
		return Math.max(1, n);
	}

	private void missing(CharacterEntity body, String key, String what) {
		if (missingSaid.equals(key + what)) return;
		missingSaid = key + what;
		String name = body.record().map(Character::name).orElse("?");
		say(body, Component.translatable(key, name, what));
	}

	// ---- placing

	private void build(CharacterEntity body, RoeServer roe, ServerLevel level, Character me) {
		if (stepping > 0) {
			stepping--;
			return;
		}
		double reach = Math.min(MAX_REACH, REACH + unreached / REACH_GROWTH_TICKS);
		if (!walk(body, spot, reach)) {
			unreached++;
			return;
		}
		if (!wanted.isAir() && !wanted.getCollisionShape(level, spot).isEmpty() && occupied(level, body, spot)) {
			stepAway(body);
			return;
		}
		if (!waitFor(body, Balance.PLACE_BLOCK_TICKS)) return;
		if (wanted.isAir()) {
			level.destroyBlock(spot, false, body);
		} else {
			if (!block.item().isEmpty()) {
				Optional<Item> item = item(block.item());
				if (item.isEmpty() || !useOne(body, item.get())) {
					phase = Phase.PLAN;
					return;
				}
			}
			if (!level.getBlockState(spot).isAir()) level.destroyBlock(spot, false, body);
			level.setBlock(spot, wanted, Block.UPDATE_ALL);
			level.playSound(null, spot, wanted.getSoundType().getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 0.8F);
		}
		Building.blocksBuilt(roe.state(), site.id(), 1, me.id(), roe.now(), roe.content().blueprints());
		unreached = 0;
		phase = Phase.PLAN;
	}

	private static boolean occupied(ServerLevel level, CharacterEntity body, BlockPos p) {
		AABB box = new AABB(p);
		return !level.getEntitiesOfClass(LivingEntity.class, box, e -> !e.isSpectator()).isEmpty();
	}

	/** The body stands where the block must go: it moves a few blocks away first. */
	private void stepAway(CharacterEntity body) {
		Vec3 away = body.position().subtract(Vec3.atCenterOf(spot));
		Vec3 dir = away.horizontalDistanceSqr() < 0.01 ? new Vec3(1, 0, 0) : new Vec3(away.x, 0, away.z).normalize();
		Vec3 to = body.position().add(dir.scale(3.0));
		body.stopWalking();
		body.getNavigation().moveTo(to.x, to.y, to.z, 1.0);
		stepping = STEP_AWAY_TICKS;
	}
}
