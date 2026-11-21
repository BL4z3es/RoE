package io.github.bl4z3es.riseofempires.mc.job;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Condition;
import io.github.bl4z3es.riseofempires.core.model.Profession;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.sim.NeedsRules;
import io.github.bl4z3es.riseofempires.core.sim.Stores;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import io.github.bl4z3es.riseofempires.mc.entity.CharacterEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;

/**
 * Shared parts of the three jobs: timed work, carrying, and the walk to the store to hand goods in.
 * Each tick the body calls {@link #tick}; a job keeps its own state between ticks.
 */
public abstract class WorkJob implements CharacterEntity.Job {
	/** Distance at which a body can use a chest. */
	protected static final double CHEST_REACH = 2.2;
	/** Ticks of walking toward one target before it is given up. PROPOSED: not in Balance yet. */
	protected static final int GIVE_UP_TICKS = 600;
	/** Ticks to wait before trying a blocked store trip again. PROPOSED: not in Balance yet. */
	private static final int RETRY_TICKS = 400;

	private int waited;
	private int walking;
	private boolean depositing;
	private long retryAt;

	public abstract Profession profession();

	/** One tick of the job's own work. Called only for a living, resident person with this profession. */
	protected abstract void work(CharacterEntity body, RoeServer roe, Settlement st, Character me);

	@Override
	public final void tick(CharacterEntity body) {
		RoeServer roe = RoeServer.get().orElse(null);
		if (roe == null) return;
		Optional<Character> me = body.record();
		if (me.isEmpty() || me.get().condition().isGone() || me.get().profession() != profession()) return;
		Optional<Settlement> st = roe.state().settlement(me.get().settlementId());
		if (st.isEmpty()) return;
		work(body, roe, st.get(), me.get());
	}

	// ---- timed work

	/** Counts one tick of a wait of {@code baseTicks}, slowed by the person's condition. True when it is over. */
	protected final boolean waitFor(CharacterEntity body, int baseTicks) {
		double m = NeedsRules.outputMultiplier(body.record().map(Character::condition).orElse(Condition.ILL));
		if (m <= 0) return false;
		if (waited++ % 8 == 0) body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
		if (waited >= (int) Math.ceil(baseTicks / m)) {
			waited = 0;
			return true;
		}
		return false;
	}

	protected final void resetWait() { waited = 0; }

	/** Walks toward a target and counts the ticks spent; {@link #gaveUp()} says when it took too long. */
	protected final boolean walk(CharacterEntity body, BlockPos target, double within) {
		if (body.walkTo(target, within)) {
			walking = 0;
			return true;
		}
		walking++;
		return false;
	}

	protected final boolean gaveUp() { return walking > GIVE_UP_TICKS; }

	protected final int walkingTicks() { return walking; }

	protected final void resetWalk() { walking = 0; }

	// ---- the store

	protected final boolean waitingForRetry(ServerLevel level) { return level.getGameTime() < retryAt; }

	protected final void retryAfter(ServerLevel level, int ticks) { retryAt = level.getGameTime() + ticks; }

	/** Starts a trip to the store if there is one and the body carries anything. */
	protected final boolean startDeposit(CharacterEntity body, RoeServer roe, Settlement st) {
		if (body.carriedCount() == 0 || waitingForRetry((ServerLevel) body.level())) return false;
		if (JobStore.of(roe, st).isEmpty()) return false;
		depositing = true;
		resetWait();
		resetWalk();
		return true;
	}

	protected final boolean depositing() { return depositing; }

	/** One step of handing everything carried in at the store. True while the trip is under way. */
	protected final boolean depositTrip(CharacterEntity body, RoeServer roe, Settlement st, Character me) {
		if (!depositing) return false;
		ServerLevel level = (ServerLevel) body.level();
		Optional<JobStore> store = JobStore.of(roe, st);
		if (store.isEmpty() || body.carriedCount() == 0) {
			depositing = false;
			resetWait();
			resetWalk();
			return false;
		}
		JobStore.Chest chest = store.get().nearest(body.position());
		if (!walk(body, chest.pos(), CHEST_REACH)) {
			if (gaveUp()) {
				depositing = false;
				resetWalk();
				retryAfter(level, RETRY_TICKS);
			}
			return true;
		}
		if (!waitFor(body, Balance.DEPOSIT_TICKS)) return true;
		Map<String, Integer> moved = new LinkedHashMap<>();
		for (JobStore.Chest c : store.get().chests()) {
			body.depositAll(c.container()).forEach((k, v) -> moved.merge(k, v, Integer::sum));
		}
		if (!moved.isEmpty()) Stores.deposit(roe.state(), st.id(), me.id(), moved, roe.now());
		else retryAfter(level, RETRY_TICKS);
		depositing = false;
		return false;
	}

	// ---- carrying

	/** Uses up one carried item. Returns false if none is carried. */
	protected static boolean useOne(CharacterEntity body, Item item) {
		SimpleContainer tmp = new SimpleContainer(CharacterEntity.CARRY_SLOTS);
		body.depositAll(tmp);
		boolean used = false;
		for (int i = 0; i < tmp.getContainerSize(); i++) {
			ItemStack s = tmp.getItem(i);
			if (!used && s.is(item)) {
				s.shrink(1);
				used = true;
			}
			if (!s.isEmpty()) body.carry(s);
		}
		return used;
	}

	protected static Optional<Item> item(String id) { return BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)); }

	protected static Map<String, Integer> count(List<ItemStack> stacks) {
		Map<String, Integer> out = new LinkedHashMap<>();
		for (ItemStack s : stacks) out.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
		return out;
	}

	/** Tells the players within hearing something the person says. */
	protected static void say(CharacterEntity body, Component text) {
		for (ServerPlayer p : ((ServerLevel) body.level()).players()) {
			if (p.distanceToSqr(body) <= 64.0 * 64.0) p.sendSystemMessage(text);
		}
	}
}
