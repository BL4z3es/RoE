package io.github.bl4z3es.riseofempires.mc.raid;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.mc.entity.CharacterEntity;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A barbarian (task M1-20, embodied half, and the ruin guards of M1-09). Weak and quick to die: leather
 * armour, a diamond axe and {@link Balance#RAIDER_HEALTH} health. It attacks players and character bodies
 * that come near; otherwise it follows its order, set by {@link RaidDirector}.
 */
public class RaiderEntity extends PathfinderMob {
	/** What a raider is doing when nobody is fighting it. */
	public enum Order { NONE, ASSAULT, RETREAT, GUARD }

	/** PROPOSED: not in Balance yet. How near a target must be before a raider goes for it. */
	public static final double ATTACK_RANGE = 16.0;
	/** PROPOSED: not in Balance yet. How far a ruin guard strays from the ruin. */
	public static final int GUARD_HOME_RADIUS = 12;
	private static final double LEG_BLOCKS = 24.0;

	/** Raiders currently loaded in any world; kept by the director. */
	static final Set<RaiderEntity> LIVE = ConcurrentHashMap.newKeySet();

	private long raidId;
	private Order order = Order.NONE;
	private @Nullable BlockPos target;
	private int ticksAtTarget;

	public RaiderEntity(EntityType<? extends RaiderEntity> type, Level level) {
		super(type, level);
		setPersistenceRequired();
		setCanPickUpLoot(false);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, Balance.RAIDER_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, CharacterEntity.BASE_SPEED)
				.add(Attributes.FOLLOW_RANGE, 40.0)
				.add(Attributes.ATTACK_DAMAGE, 1.0);
	}

	/** Creates a raider for a raid (0 for a ruin guard) and adds it to the world. Null if refused. */
	public static @Nullable RaiderEntity spawn(ServerLevel level, long raidId, BlockPos pos) {
		RaiderEntity r = RaidEntities.RAIDER.create(level, EntitySpawnReason.EVENT);
		if (r == null) return null;
		r.raidId = raidId;
		r.equip();
		r.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		r.setYRot(level.getRandom().nextFloat() * 360.0F);
		return level.addFreshEntity(r) ? r : null;
	}

	private void equip() {
		setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_AXE));
		setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
		setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
		setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
	}

	public long raidId() { return raidId; }
	public Order order() { return order; }
	public @Nullable BlockPos orderTarget() { return target; }
	/** Ticks this raider has been within 3 blocks of its assault target. */
	public int ticksAtTarget() { return ticksAtTarget; }

	public void giveOrder(Order o, @Nullable BlockPos at) {
		if (o == order && Objects.equals(at, target)) return;
		order = o;
		target = at;
		ticksAtTarget = 0;
		getNavigation().stop();
	}

	// ---- goals

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new RaiderMeleeGoal(this));
		goalSelector.addGoal(4, new FollowOrderGoal(this));
		goalSelector.addGoal(5, new GuardHomeGoal(this));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
		goalSelector.addGoal(9, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new Near<>(this, Player.class, true));
		targetSelector.addGoal(3, new Near<>(this, CharacterEntity.class, true));
	}

	/** Looks for targets only within {@link #ATTACK_RANGE}. */
	private static final class Near<T extends LivingEntity> extends NearestAttackableTargetGoal<T> {
		Near(RaiderEntity mob, Class<T> type, boolean mustSee) { super(mob, type, mustSee); }

		@Override
		protected double getFollowDistance() { return ATTACK_RANGE; }
	}

	private boolean awayFromHome(Entity e) {
		return order == Order.GUARD && target != null
				&& e.distanceToSqr(Vec3.atCenterOf(target)) > (double) GUARD_HOME_RADIUS * GUARD_HOME_RADIUS;
	}

	/** Melee that a guard drops when the target is beyond its home radius. */
	private static final class RaiderMeleeGoal extends MeleeAttackGoal {
		private final RaiderEntity raider;

		RaiderMeleeGoal(RaiderEntity raider) {
			super(raider, 1.0, false);
			this.raider = raider;
		}

		@Override
		public boolean canUse() {
			LivingEntity t = raider.getTarget();
			return t != null && !raider.awayFromHome(t) && super.canUse();
		}

		@Override
		public boolean canContinueToUse() {
			LivingEntity t = raider.getTarget();
			return t != null && !raider.awayFromHome(t) && super.canContinueToUse();
		}
	}

	/** Walks, in legs, to the order's target. */
	private static final class FollowOrderGoal extends Goal {
		private final RaiderEntity raider;

		FollowOrderGoal(RaiderEntity raider) {
			this.raider = raider;
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return (raider.order == Order.ASSAULT || raider.order == Order.RETREAT) && raider.target != null;
		}

		@Override
		public boolean canContinueToUse() { return canUse(); }

		@Override
		public void tick() {
			BlockPos t = raider.target;
			if (t == null) return;
			double reach = raider.order == Order.ASSAULT ? 2.0 : 4.0;
			if (raider.position().distanceToSqr(Vec3.atCenterOf(t)) <= reach * reach) {
				raider.getNavigation().stop();
				return;
			}
			if (raider.getNavigation().isDone() || raider.tickCount % 40 == 0) raider.pathLeg(t);
		}
	}

	/** Keeps a ruin guard within its home radius. */
	private static final class GuardHomeGoal extends Goal {
		private final RaiderEntity raider;

		GuardHomeGoal(RaiderEntity raider) {
			this.raider = raider;
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() { return raider.order == Order.GUARD && raider.target != null && raider.getNavigation().isDone(); }

		@Override
		public void start() {
			BlockPos home = raider.target;
			Vec3 homeVec = Vec3.atBottomCenterOf(home);
			double inner = (GUARD_HOME_RADIUS - 2) * (double) (GUARD_HOME_RADIUS - 2);
			if (raider.position().distanceToSqr(homeVec) > inner) {
				raider.getNavigation().moveTo(raider.getNavigation().createPath(home, 1), 0.8);
				return;
			}
			if (raider.getRandom().nextInt(4) != 0) return;
			Vec3 v = DefaultRandomPos.getPosTowards(raider, 6, 3, homeVec, Math.PI);
			if (v != null && v.distanceToSqr(homeVec) <= inner) raider.getNavigation().moveTo(v.x, v.y, v.z, 0.6);
		}

		@Override
		public boolean canContinueToUse() { return false; }
	}

	/** One path request of at most about {@link #LEG_BLOCKS} blocks towards the target. */
	private void pathLeg(BlockPos t) {
		Vec3 aim = Vec3.atBottomCenterOf(t);
		if (position().distanceTo(aim) > LEG_BLOCKS) {
			Vec3 leg = DefaultRandomPos.getPosTowards(this, (int) LEG_BLOCKS - 4, 7, aim, Math.PI / 4);
			if (leg != null) aim = leg;
		}
		getNavigation().moveTo(getNavigation().createPath(BlockPos.containing(aim), 0), 1.0);
	}

	// ---- each tick

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (order == Order.ASSAULT && target != null
				&& Math.abs(getY() - target.getY()) <= 4
				&& Math.hypot(getX() - (target.getX() + 0.5), getZ() - (target.getZ() + 0.5)) <= 3.0) {
			ticksAtTarget++;
		} else {
			ticksAtTarget = 0;
		}
		// A retreating raider is never removed in view: only once no player is within the Embodied radius.
		if (order == Order.RETREAT && tickCount % 20 == 0
				&& level.getNearestPlayer(this, Balance.EMBODIED_RADIUS_BLOCKS) == null) {
			discard();
		}
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!level().isClientSide() && raidId != 0) RaidDirector.raiderDied(this, source);
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) { return false; }

	@Override
	public void onRemoval(RemovalReason reason) {
		super.onRemoval(reason);
		LIVE.remove(this);
	}

	// ---- saving

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putLong("RaidId", raidId);
		output.putString("Order", order.name());
		if (target != null) {
			output.putInt("TargetX", target.getX());
			output.putInt("TargetY", target.getY());
			output.putInt("TargetZ", target.getZ());
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		raidId = input.getLongOr("RaidId", 0L);
		try {
			order = Order.valueOf(input.getStringOr("Order", "NONE"));
		} catch (IllegalArgumentException e) {
			order = Order.NONE;
		}
		target = input.getInt("TargetX").isPresent()
				? new BlockPos(input.getIntOr("TargetX", 0), input.getIntOr("TargetY", 0), input.getIntOr("TargetZ", 0)) : null;
	}
}
