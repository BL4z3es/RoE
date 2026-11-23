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
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
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
 * Tasks M1-13 and M1-14: builds the settlement's active blueprint, one block at a time, in view.
 *
 * <p>Several builders share one site. Each claims the next free blueprint index in build order, so
 * two builders never place the same block, and claims stay within a few blocks of the site's placed
 * prefix, so the building grows in order and a block never floats where its support is not yet
 * standing. A builder that stops working (illness, night, the area unloading) stops refreshing its
 * claim's heartbeat, so the claim lapses and another builder takes the block. The site's
 * {@code placed} counter advances only over a contiguous prefix of finished blocks, and the Ledger
 * event for each stretch names the builder who placed those blocks (rule E2).
 *
 * <p>Claims live in memory only. After a save and reload they are rebuilt from world state: the
 * plan step finds the blocks already standing, counts them through {@link Building#blocksBuilt},
 * and claiming resumes after them. No block is placed twice: a block is set only by the builder
 * holding its claim, and a lapsed claim is taken up only after its heartbeat has run out.
 *
 * <p>A builder with nothing left to claim (every remaining block is done or held) hands back what
 * it carries and waits by the camp centre, so it is never seen standing idle on the site.
 */
public final class BuilderJob extends WorkJob {
	private enum Phase { PLAN, FETCH, BUILD }

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
	private static final int MISSING_RETRY_TICKS = 100;
	/** Ticks spent walking off a block's spot before trying again. PROPOSED. */
	private static final int STEP_AWAY_TICKS = 20;
	/**
	 * A block claim lapses after this many ticks without its holder refreshing it; another builder
	 * may then take the block. Must outlast a round trip to the store and back with materials,
	 * which the rates in WorkRates put at a few hundred ticks. PROPOSED.
	 */
	private static final int CLAIM_LAPSE_TICKS = 1200;
	/**
	 * How far ahead of the site's placed prefix a builder may claim, in blocks. Keeps builders
	 * working side by side in build order, so a block is not set before the blocks under it.
	 * Must be at least the number of builders that share a site. PROPOSED.
	 */
	private static final int CLAIM_MAX_LEAD_BLOCKS = 4;

	// -----------------------------------------------------------------------------------------------
	// Per-site block claims, keyed by site id. One record per site being built in view.
	//
	// done:   indices whose block stands in the world (set the moment a builder sets the block, or
	//         the plan step finds it already correct). site.placed is the contiguous done prefix.
	// placer: index -> character id of the builder who set it, kept until the block is counted, so
	//         the Ledger event for a stretch of the prefix names the builders who placed it.
	// live:   index -> {holder character id, last heartbeat tick} for claims in flight.
	//
	// claim() hands out the lowest index that is neither done nor live-claimed and lies within
	// CLAIM_MAX_LEAD_BLOCKS of the placed prefix, after sweeping lapsed claims. The server tick is
	// single threaded, so two builders can never claim the same index at once.
	// -----------------------------------------------------------------------------------------------

	private static final Map<Long, SiteClaims> CLAIMS = new HashMap<>();

	private static final class SiteClaims {
		final BitSet done = new BitSet();
		final Map<Integer, Long> placer = new HashMap<>();
		final Map<Integer, long[]> live = new HashMap<>();

		/** Claims the lowest free index within the lead of the placed prefix, or -1 if none is free. */
		int claim(long characterId, long gameTime, int placed, int total) {
			for (Iterator<long[]> it = live.values().iterator(); it.hasNext(); ) {
				if (gameTime - it.next()[1] > CLAIM_LAPSE_TICKS) it.remove();
			}
			// One claim per builder per site. A builder asking again (its job may have been made anew,
			// after a reload or a job change) gets the block it already holds, never a second one,
			// so no block is left locked by a claim nobody is working.
			for (Map.Entry<Integer, long[]> e : live.entrySet()) {
				if (e.getValue()[0] == characterId && !done.get(e.getKey())) {
					e.getValue()[1] = gameTime;
					return e.getKey();
				}
			}
			int limit = Math.min(total, placed + CLAIM_MAX_LEAD_BLOCKS);
			for (int i = placed; i < limit; i++) {
				if (done.get(i) || live.containsKey(i)) continue;
				live.put(i, new long[] {characterId, gameTime});
				placer.put(i, characterId);
				return i;
			}
			return -1;
		}

		/** Marks a block finished: it stands in the world and can no longer be claimed. */
		void markDone(int index) {
			done.set(index);
			live.remove(index);
		}

		/** The builder who placed a done block, or {@code fallback} when nobody recorded it. */
		long placerOf(int index, long fallback) { return placer.getOrDefault(index, fallback); }

		/** Forgets who placed the counted stretch. */
		void forgetPlacers(int from, int to) {
			for (int i = from; i < to; i++) placer.remove(i);
		}

		/** True if this builder still holds the claim on the index. */
		boolean holds(int index, long characterId) {
			long[] c = live.get(index);
			return c != null && c[0] == characterId;
		}

		/** Drops a live claim if this builder holds it; a finished block stays done. */
		void release(int index, long characterId) {
			if (holds(index, characterId)) live.remove(index);
		}

		/** Refreshes the heartbeat on a claim its holder is still working on. */
		void hold(int index, long gameTime) {
			long[] c = live.get(index);
			if (c != null) c[1] = gameTime;
		}
	}

	private Phase phase = Phase.PLAN;
	private BuildSite site;
	private Blueprint blueprint;
	private BlockPos spot;
	private BlockState wanted;
	private BlockState supportWanted;
	private BlueprintBlock block;
	private int claimIndex = -1;
	/** The character this job's claims belong to (set when a claim is taken). */
	private long heldBy;
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
			// No site to build: the old site's claims are moot; hand back anything carried, then idle.
			if (site != null) CLAIMS.remove(site.id());
			releaseClaim();
			site = null;
			phase = Phase.PLAN;
			startDeposit(body, roe, st);
			return;
		}
		if (site != null && site.id() != active.get().id()) {
			// The old site finished and a new one took its place; its claims are moot.
			CLAIMS.remove(site.id());
			releaseClaim();
		}
		site = active.get();
		blueprint = roe.content().blueprints().get(site.blueprintId());
		if (blueprint == null) return;
		// A claim counts only while its holder keeps working on it.
		if (claimIndex >= 0) {
			SiteClaims sc = claims();
			if (sc.done.get(claimIndex) || !sc.holds(claimIndex, me.id())) {
				releaseClaim();
				phase = Phase.PLAN;
			} else {
				sc.hold(claimIndex, level.getGameTime());
			}
		}
		if (phase == Phase.PLAN) plan(body, roe, st, level, me);
		switch (phase) {
			case FETCH -> fetch(body, roe, st, level, me);
			case BUILD -> build(body, roe, level, me);
			default -> { }
		}
	}

	private SiteClaims claims() {
		return CLAIMS.computeIfAbsent(site.id(), k -> new SiteClaims());
	}

	/** Gives the held block back to the site, so another builder can take it at once rather than after a lapse. */
	private void releaseClaim() {
		if (claimIndex >= 0 && site != null) {
			SiteClaims sc = CLAIMS.get(site.id());
			if (sc != null) sc.release(claimIndex, heldBy);
		}
		claimIndex = -1;
	}

	// ---- planning: count what already stands, claim the next block, decide to fetch or build

	private void plan(CharacterEntity body, RoeServer roe, Settlement st, ServerLevel level, Character me) {
		// Blocks already standing (placed earlier, or by another tier) count without being rebuilt.
		int skipped = 0;
		while (skipped < SKIP_BATCH && site.placed() + skipped < site.total()) {
			BlueprintBlock b = blueprint.blocks().get(site.placed() + skipped);
			BlockPos p = RoeServer.pos(Building.worldPos(site, b));
			if (!level.isLoaded(p) || !correct(level, p, WorldBlocks.parse(level, b.blockState(), site.rotation()))) break;
			skipped++;
		}
		if (skipped > 0) {
			SiteClaims sc = claims();
			for (int i = 0; i < skipped; i++) sc.markDone(site.placed() + i);
			advanceAndCount(roe, me);
			return;
		}
		// A builder already holding a claim keeps working it. Claiming again would orphan the held
		// block, which then stays unbuildable until its claim lapses (CLAIM_LAPSE_TICKS).
		if (claimIndex >= 0 && claims().holds(claimIndex, me.id()) && !claims().done.get(claimIndex)) {
			prepareTarget(body, roe, level);
			return;
		}
		// Claim the next block in build order. Without one, hand back what is carried and wait by
		// the camp centre until a block frees up; the walk keeps the waiting visible.
		int idx = claims().claim(me.id(), level.getGameTime(), site.placed(), site.total());
		if (idx < 0) {
			releaseClaim();
			startDeposit(body, roe, st);
			walk(body, RoeServer.pos(st.centre()), 4.0);
			return;
		}
		claimIndex = idx;
		heldBy = me.id();
		prepareTarget(body, roe, level);
	}

	/** Sets up the claimed block's spot, state and item, then fetches or builds it. */
	private void prepareTarget(CharacterEntity body, RoeServer roe, ServerLevel level) {
		block = blueprint.blocks().get(claimIndex);
		spot = RoeServer.pos(Building.worldPos(site, block));
		if (!level.isLoaded(spot)) return;
		wanted = WorldBlocks.parse(level, block.blockState(), site.rotation());
		// The block this one stands on, when the blueprint builds that too: placing this block
		// before its support stands would leave it floating or pop it off.
		supportWanted = null;
		BlockPos under = spot.below();
		for (int i = site.placed(); i < site.total(); i++) {
			if (RoeServer.pos(Building.worldPos(site, blueprint.blocks().get(i))).equals(under)) {
				supportWanted = WorldBlocks.parse(level, blueprint.blocks().get(i).blockState(), site.rotation());
				break;
			}
		}
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

	/**
	 * Counts every contiguous finished block from the site's placed prefix, one Ledger event per
	 * stretch placed by the same builder, from fresh state: another builder may have advanced the
	 * site since we last read it.
	 */
	private void advanceAndCount(RoeServer roe, Character me) {
		SiteClaims sc = claims();
		BuildSite fresh = roe.state().buildSite(site.id()).orElseThrow();
		int p = fresh.placed();
		while (p < site.total() && sc.done.get(p)) {
			long placer = sc.placerOf(p, me.id());
			int end = p + 1;
			while (end < site.total() && sc.done.get(end) && sc.placerOf(end, me.id()) == placer) end++;
			Building.blocksBuilt(roe.state(), site.id(), end - p, placer, roe.now(), roe.content().blueprints());
			sc.forgetPlacers(p, end);
			p = end;
		}
	}

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
		// The block this one stands on is still being placed by another builder: wait for it.
		if (!wanted.isAir() && supportWanted != null && !correct(level, spot.below(), supportWanted)) return;
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
		// The block stands. Count it and every contiguous finished block behind it.
		SiteClaims sc = claims();
		sc.markDone(claimIndex);
		advanceAndCount(roe, me);
		releaseClaim();
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
