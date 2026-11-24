package io.github.bl4z3es.riseofempires.mc.entity;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Condition;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.sim.GameTime;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The body of a {@link Character} record (task M1-07). The record is the person; this is the part
 * of them that walks about when their area is loaded. It carries items, uses containers, sleeps in
 * beds under a roof, and shows its condition (tasks M1-11 and M1-15, embodied half).
 *
 * <p>It never despawns. It saves only the character id and what it carries; everything else is read
 * from the record in {@link RoeServer}.
 */
public class CharacterEntity extends PathfinderMob {
	/** Something a body does each tick: farm, build, gather. Null means idle. */
	public interface Job {
		void tick(CharacterEntity body);
	}

	public static final int CARRY_SLOTS = 9;

	/**
	 * Movement speed attribute that gives about {@link Balance#WALK_TICKS_PER_BLOCK} ticks per block at
	 * navigation speed 1.0. PROPOSED: tuned by measurement in CharacterGameTest, not from the design.
	 */
	public static final double BASE_SPEED = 0.26;
	/** Speed factors for STRAINED, WEAK and ILL bodies. PROPOSED: not in Balance yet. */
	public static final double SPEED_STRAINED = 0.85;
	public static final double SPEED_WEAK = 0.7;
	public static final double SPEED_ILL = 0.5;
	/** Night, as time of day: bodies look for a bed from here. PROPOSED: not in Balance yet. */
	public static final int NIGHT_BEGINS = 12500;
	public static final int NIGHT_ENDS = 23500;
	/** Ticks between checks of the record. */
	private static final int CHECK_INTERVAL = 20;
	/** Ticks to wait before asking navigation for the same unreachable target again. */
	private static final int REPATH_COOLDOWN = 10;
	/** Farthest a single path request reaches; longer walks go in legs. */
	private static final double LEG_BLOCKS = 24.0;
	/** A departed person walks this far from the camp, until out of every player's range. */
	private static final double LEAVE_WALK_BLOCKS = 80.0;
	private static final int BED_SEARCH_INTERVAL = 100;

	private long characterId;
	private final SimpleContainer inventory = new SimpleContainer(CARRY_SLOTS);
	private @Nullable Job job;
	private @Nullable BlockPos lastTarget;
	private int repathCooldown;
	private @Nullable Vec3 pathStart;
	private @Nullable Condition lastCondition;
	private @Nullable BlockPos bed;
	private long bedSearchAt;
	private long markedNight = Long.MIN_VALUE;

	public CharacterEntity(EntityType<? extends CharacterEntity> type, Level level) {
		super(type, level);
		setPersistenceRequired();
		setCanPickUpLoot(false);
		setDropChance(EquipmentSlot.MAINHAND, 0.0F);
		if (getNavigation() instanceof GroundPathNavigation nav) {
			nav.setCanOpenDoors(true);
			nav.setCanFloat(true);
		}
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, BASE_SPEED)
				.add(Attributes.FOLLOW_RANGE, 48.0);
	}

	/** Creates the body of a character and adds it to the world. Returns null if the world refused it. */
	public static @Nullable CharacterEntity spawn(ServerLevel level, long characterId, BlockPos pos) {
		CharacterEntity body = RoeEntities.CHARACTER.create(level, EntitySpawnReason.EVENT);
		if (body == null) return null;
		body.characterId = characterId;
		body.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		body.setYRot(level.getRandom().nextFloat() * 360.0F);
		body.refreshFromRecord();
		return level.addFreshEntity(body) ? body : null;
	}

	// ---- identity

	public long characterId() { return characterId; }

	public Optional<Character> record() {
		return RoeServer.get().flatMap(r -> r.state().character(characterId));
	}

	private Optional<Settlement> settlement() {
		return RoeServer.get().flatMap(r -> record().flatMap(c -> r.state().settlement(c.settlementId())));
	}

	// ---- saving

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putLong("CharacterId", characterId);
		inventory.storeAsItemList(output.list("Carried", ItemStack.CODEC));
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		characterId = input.getLongOr("CharacterId", 0L);
		inventory.clearContent();
		input.list("Carried", ItemStack.CODEC).ifPresent(inventory::fromItemList);
		syncHeld();
	}

	// ---- goals: idle behaviour only

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new OpenDoorGoal(this, true));
		goalSelector.addGoal(5, new StrollInClaimGoal(this));
		goalSelector.addGoal(6, new LookAtNearbyPlayerGoal(this));
		goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	private boolean freeToIdle() {
		Condition c = lastCondition;
		return job == null && !isSleeping() && bed == null && !(c != null && c.isGone()) && !isNight();
	}

	/** Strolls to a spot inside the settlement claim, only while idle. */
	private static final class StrollInClaimGoal extends RandomStrollGoal {
		private final CharacterEntity body;

		StrollInClaimGoal(CharacterEntity body) {
			super(body, 0.8, 120, false);
			this.body = body;
		}

		@Override
		public boolean canUse() { return body.freeToIdle() && super.canUse(); }

		@Override
		public boolean canContinueToUse() { return body.freeToIdle() && super.canContinueToUse(); }

		@Override
		protected @Nullable Vec3 getPosition() {
			Optional<Settlement> st = body.settlement();
			for (int i = 0; i < 5; i++) {
				Vec3 v = LandRandomPos.getPos(body, 10, 7);
				if (v == null) continue;
				if (st.isEmpty()) return v;
				if (st.get().claims(RoeServer.place((ServerLevel) body.level(), BlockPos.containing(v)))) return v;
			}
			return null;
		}
	}

	private static final class LookAtNearbyPlayerGoal extends LookAtPlayerGoal {
		private final CharacterEntity body;

		LookAtNearbyPlayerGoal(CharacterEntity body) {
			super(body, Player.class, 8.0F);
			this.body = body;
		}

		@Override
		public boolean canUse() { return !body.isSleeping() && super.canUse(); }
	}

	// ---- walking

	/**
	 * Starts or continues walking to {@code target}. Returns true once the body is within
	 * {@code within} blocks of it. Call it every tick from a job; it only asks for a new path when the
	 * last one is finished or the target changed.
	 */
	public boolean walkTo(BlockPos target, double within) {
		Vec3 goal = Vec3.atCenterOf(target);
		if (position().distanceToSqr(goal) <= within * within) {
			stopWalking();
			return true;
		}
		if (repathCooldown > 0) repathCooldown--;
		boolean changed = !target.equals(lastTarget);
		if (changed || (navigation.isDone() && repathCooldown == 0)) {
			lastTarget = target.immutable();
			// A path that moved the body on is followed at once by the next leg; one that went nowhere waits.
			repathCooldown = pathStart != null && pathStart.distanceToSqr(position()) < 1.0 && !changed ? REPATH_COOLDOWN : 0;
			pathStart = position();
			BlockPos stand = standingPlaceNear(target);
			Vec3 aim = Vec3.atBottomCenterOf(stand);
			if (position().distanceTo(aim) > LEG_BLOCKS) {
				Vec3 leg = DefaultRandomPos.getPosTowards(this, (int) LEG_BLOCKS - 4, 7, aim, Math.PI / 4);
				if (leg != null) aim = leg;
			}
			navigation.moveTo(navigation.createPath(BlockPos.containing(aim), 0), 1.0);
		}
		return false;
	}

	/**
	 * A chest or a bed cannot be stood in, so a path straight to it has no end. Returns the block
	 * beside it where a body can stand and reach it, the one nearest to this body.
	 */
	private BlockPos standingPlaceNear(BlockPos target) {
		if (isOpenAt(target) && !level().getBlockState(target.below()).getCollisionShape(level(), target.below()).isEmpty()) return target;
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos p = target.relative(d);
			if (!isOpenAt(p) || level().getBlockState(p.below()).getCollisionShape(level(), p.below()).isEmpty()) continue;
			double dist = p.distToCenterSqr(position());
			if (dist < bestDist) {
				bestDist = dist;
				best = p;
			}
		}
		return best != null ? best : target;
	}

	private boolean isOpenAt(BlockPos p) {
		return level().getBlockState(p).getCollisionShape(level(), p).isEmpty()
				&& level().getBlockState(p.above()).getCollisionShape(level(), p.above()).isEmpty();
	}

	public void stopWalking() {
		navigation.stop();
		lastTarget = null;
		pathStart = null;
	}

	// ---- carrying

	/** Puts the stack in the carried slots. Returns what did not fit. */
	public ItemStack carry(ItemStack stack) {
		ItemStack rest = inventory.addItem(stack.copy());
		syncHeld();
		return rest;
	}

	public int carriedCount() {
		int n = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) n += inventory.getItem(i).getCount();
		return n;
	}

	/** Item id to count for everything carried. */
	public Map<String, Integer> carried() {
		Map<String, Integer> out = new LinkedHashMap<>();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack s = inventory.getItem(i);
			if (!s.isEmpty()) out.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
		}
		return out;
	}

	/** Moves everything it can into the container. Returns what moved, item id to count. */
	public Map<String, Integer> depositAll(Container c) {
		Map<String, Integer> moved = new LinkedHashMap<>();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack s = inventory.getItem(i);
			if (s.isEmpty()) continue;
			int before = s.getCount();
			ItemStack rest = HopperBlockEntity.addItem(null, c, s.copy(), null);
			int did = before - rest.getCount();
			if (did > 0) {
				moved.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), did, Integer::sum);
				inventory.setItem(i, rest);
			}
		}
		if (!moved.isEmpty()) c.setChanged();
		syncHeld();
		return moved;
	}

	/** Takes up to {@code count} of the item from the container. Returns how many it took. */
	public int takeFrom(Container c, Item item, int count) {
		int taken = 0;
		for (int i = 0; i < c.getContainerSize() && taken < count; i++) {
			ItemStack s = c.getItem(i);
			if (s.isEmpty() || !s.is(item)) continue;
			int want = Math.min(s.getCount(), count - taken);
			ItemStack rest = inventory.addItem(s.copyWithCount(want));
			int did = want - rest.getCount();
			if (did > 0) {
				s.shrink(did);
				c.setItem(i, s.isEmpty() ? ItemStack.EMPTY : s);
				taken += did;
			}
			if (!rest.isEmpty()) break;
		}
		if (taken > 0) c.setChanged();
		syncHeld();
		return taken;
	}

	private void syncHeld() {
		ItemStack first = ItemStack.EMPTY;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (!inventory.getItem(i).isEmpty()) {
				first = inventory.getItem(i);
				break;
			}
		}
		setItemSlot(EquipmentSlot.MAINHAND, first.isEmpty() ? ItemStack.EMPTY : first.copy());
	}

	// ---- jobs

	/** A player's empty hand gives this person a job (task M1-14). */
	@Override
	protected net.minecraft.world.InteractionResult mobInteract(Player player, net.minecraft.world.InteractionHand hand) {
		return io.github.bl4z3es.riseofempires.mc.job.JobAssignment.onInteract(this, player, hand);
	}

	public void setJob(@Nullable Job job) { this.job = job; }

	public @Nullable Job job() { return job; }

	// ---- the record shows in the body

	private boolean isNight() {
		long tod = GameTime.timeOfDay(RoeServer.get().map(RoeServer::now).orElse(0L));
		return tod >= NIGHT_BEGINS && tod < NIGHT_ENDS;
	}

	private void refreshFromRecord() {
		Optional<Character> rec = record();
		if (rec.isEmpty()) return;
		Character c = rec.get();
		Component name = Component.literal(c.name());
		if (!name.equals(getCustomName())) {
			setCustomName(name);
			setCustomNameVisible(true);
		}
		lastCondition = c.condition();
		double factor = switch (c.condition()) {
			case STRAINED -> SPEED_STRAINED;
			case WEAK -> SPEED_WEAK;
			case ILL -> SPEED_ILL;
			default -> 1.0;
		};
		// A mob's walking pace grows with the square of its speed attribute (Mob.setSpeed feeds the same
		// number into both the speed and the forward input), so the square root keeps the pace at `factor`.
		double wanted = BASE_SPEED * Math.sqrt(factor);
		var speed = getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null && speed.getBaseValue() != wanted) speed.setBaseValue(wanted);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (RoeServer.get().isEmpty()) return;
		if (tickCount % CHECK_INTERVAL == 0 || lastCondition == null) {
			if (record().isEmpty()) {
				discard();
				return;
			}
			refreshFromRecord();
		}
		Condition cond = lastCondition == null ? Condition.WELL : lastCondition;
		switch (cond) {
			case DEAD -> {
				if (isAlive()) kill(level);
			}
			case LEFT -> leaveStep(level);
			default -> liveStep(level, cond);
		}
	}

	private void liveStep(ServerLevel level, Condition cond) {
		// Night is for people who belong to a camp. A survivor still walking in (they set out at dusk) has
		// none yet and keeps walking; before this, they froze where they stood until the walk timed out.
		if (isNight() && settlement().isPresent()) {
			nightStep(level);
			return;
		}
		if (isSleeping()) stopSleeping();
		bed = null;
		if (job != null && cond != Condition.ILL) job.tick(this);
	}

	// ---- leaving

	private void leaveStep(ServerLevel level) {
		if (isSleeping()) stopSleeping();
		job = null;
		bed = null;
		if (tickCount % CHECK_INTERVAL == 0 && level.getNearestPlayer(this, Balance.EMBODIED_RADIUS_BLOCKS) == null) {
			discard();
			return;
		}
		BlockPos from = settlement().map(s -> RoeServer.pos(s.centre())).orElse(blockPosition());
		Vec3 away = position().subtract(Vec3.atCenterOf(from));
		if (away.horizontalDistanceSqr() < 1.0) away = new Vec3(Math.cos(characterId), 0, Math.sin(characterId));
		Vec3 dir = new Vec3(away.x, 0, away.z).normalize();
		walkTo(BlockPos.containing(Vec3.atCenterOf(from).add(dir.scale(LEAVE_WALK_BLOCKS))), 2.0);
	}

	// ---- sleeping

	private void nightStep(ServerLevel level) {
		RoeServer roe = RoeServer.get().orElseThrow();
		if (isSleeping()) {
			long night = Math.floorDiv(roe.now() - NIGHT_ENDS, GameTime.TICKS_PER_DAY);
			if (markedNight != night && bedIsRoofed(level, getSleepingPos().orElse(null))) {
				markedNight = night;
				roe.markSlept(characterId);
			}
			return;
		}
		if (bed != null && !bedUsable(level, bed)) bed = null;
		if (bed == null && level.getGameTime() >= bedSearchAt) {
			bedSearchAt = level.getGameTime() + BED_SEARCH_INTERVAL;
			bed = findBed(level);
		}
		if (bed != null) {
			if (walkTo(bed, 1.6) && bedUsable(level, bed)) {
				if (startSleeping(bed)) {
					stopWalking();
					return;
				}
				bed = null;
			}
			return;
		}
		// No bed: stay awake by the camp centre. The core counts it as a rough night.
		settlement().ifPresent(s -> walkTo(RoeServer.pos(s.centre()), 4.0));
	}

	private static boolean bedUsable(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getBlock() instanceof BedBlock && !state.getValue(BedBlock.OCCUPIED) && bedIsRoofed(level, pos);
	}

	private static boolean bedIsRoofed(ServerLevel level, @Nullable BlockPos pos) {
		return pos != null && !level.canSeeSky(pos.above());
	}

	private @Nullable BlockPos findBed(ServerLevel level) {
		Optional<Settlement> st = settlement();
		if (st.isEmpty()) return null;
		Settlement s = st.get();
		BlockPos centre = RoeServer.pos(s.centre());
		BlockPos here = blockPosition();
		return level.getPoiManager()
				.getInRange(h -> h.is(PoiTypes.HOME), centre, s.claimRadius(), PoiManager.Occupancy.ANY)
				.map(r -> r.getPos())
				.filter(p -> s.claims(RoeServer.place(level, p)) && bedUsable(level, p))
				.min((a, b) -> Double.compare(a.distSqr(here), b.distSqr(here)))
				.orElse(null);
	}

	// ---- death

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		for (ItemStack s : inventory.removeAllItems()) spawnAtLocation(level, s);
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) { return false; }
}
