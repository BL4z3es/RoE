package io.github.bl4z3es.riseofempires.mc.entity;

import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.sim.camp.Jobs;
import io.github.bl4z3es.riseofempires.core.sim.camp.Needs;
import io.github.bl4z3es.riseofempires.mc.world.RoeWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The body of one character while their camp is watched (task M1-07). It holds the character's
 * id and nothing else of its own: name, trait and work all come from the simulation record.
 * {@link Bodies} spawns, drives and removes it.
 */
public class PersonEntity extends PathfinderMob {
    private String characterId = "";
    private String settlementId = "";
    /** What {@link Bodies} has this body doing, for anyone who asks. */
    String doing = "";
    final BodyTask task = new BodyTask();

    public PersonEntity(EntityType<? extends PersonEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 0.9, true) {
            @Override
            public boolean canUse() { return isFighter() && super.canUse(); }
        });
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, BarbarianEntity.class, 10.0F, 0.8, 1.0) {
            @Override
            public boolean canUse() { return !isFighter() && super.canUse(); }
        });
        goalSelector.addGoal(3, new OpenDoorGoal(this, true));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, BarbarianEntity.class, true) {
            @Override
            public boolean canUse() { return isFighter() && super.canUse(); }
        });
    }

    void bind(Character c) {
        characterId = c.id();
        settlementId = c.settlementId();
        setCustomName(Component.literal(c.name()));
    }

    void setSettlement(String id) { settlementId = id; }

    public String characterId() { return characterId; }
    public String settlementId() { return settlementId; }

    private Character record() {
        if (!(level() instanceof ServerLevel server)) return null;
        RoeWorld w = RoeWorld.get(server.getServer());
        return w == null ? null : w.state().character(characterId);
    }

    boolean isFighter() {
        Character c = record();
        return c != null && c.profession().equals(io.github.bl4z3es.riseofempires.core.model.Professions.FIGHTER);
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !(level() instanceof ServerLevel server)) return InteractionResult.PASS;
        RoeWorld w = RoeWorld.get(server.getServer());
        Character c = record();
        if (w == null || c == null) return InteractionResult.PASS;
        Bodies.interact(w, this, c, player);
        return InteractionResult.SUCCESS;
    }

    /** One line about this person, as the player sees them. */
    Component describe(RoeWorld w, Character c) {
        Ref r = Ref.character(c.id());
        String condition = w.state().tag(r, Needs.CONDITION);
        String idle = w.state().tag(r, Jobs.IDLE_REASON);
        String now = !doing.isEmpty() ? doing : !idle.isEmpty() ? "idle: " + idle : "resting";
        return Component.literal(c.name()).withStyle(ChatFormatting.GOLD)
                .append(Component.literal(", " + c.trait() + ", wants " + c.want() + ". "
                        + (c.profession().isEmpty() ? "No work given" : "A " + c.profession())
                        + (condition.isEmpty() ? "" : ", " + condition) + ". Now: " + now + ".").withStyle(ChatFormatting.WHITE));
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel server) {
            RoeWorld w = RoeWorld.get(server.getServer());
            if (w != null) Bodies.onDeath(w, this, source);
        }
    }
}
