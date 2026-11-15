package io.github.bl4z3es.riseofempires.mc.entity;

import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Professions;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.sim.CampRules;
import io.github.bl4z3es.riseofempires.core.sim.camp.Blueprint;
import io.github.bl4z3es.riseofempires.core.sim.camp.Food;
import io.github.bl4z3es.riseofempires.core.sim.camp.Jobs;
import io.github.bl4z3es.riseofempires.core.sim.camp.People;
import io.github.bl4z3es.riseofempires.core.sim.camp.Raids;
import io.github.bl4z3es.riseofempires.core.sim.camp.Survivors;
import io.github.bl4z3es.riseofempires.core.sim.camp.WorkOrder;
import io.github.bl4z3es.riseofempires.mc.McTuning;
import io.github.bl4z3es.riseofempires.mc.world.Blueprints;
import io.github.bl4z3es.riseofempires.mc.world.RoeWorld;
import io.github.bl4z3es.riseofempires.mc.world.Sites;
import io.github.bl4z3es.riseofempires.mc.world.Stockpiles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Gives people bodies while their camp is watched, and drives those bodies through the same
 * work orders the Ledger tier uses ({@link Jobs#nextOrder}, {@link Jobs#begin},
 * {@link Jobs#complete}), so watched and unwatched camps keep the same books (rule E3).
 *
 * <p>Bodies appear only out of every player's sight: at the camp when no player is near it, or
 * out at the edge of sight walking in. They are removed the same way. Nothing stands idle
 * without a reason a player can ask for (right-click).
 */
public final class Bodies {
    private Bodies() {}

    private static final Map<String, PersonEntity> BODIES = new HashMap<>();

    public static void register() {
        RoeWorld.onTick(Bodies::tick);
    }

    /** The body of a character, if one is in the world. */
    public static PersonEntity body(String characterId) {
        PersonEntity e = BODIES.get(characterId);
        return e == null || e.isRemoved() ? null : e;
    }

    private static void tick(RoeWorld w) {
        if (w.ticks() % 10 == 0) reconcile(w);
        for (PersonEntity body : List.copyOf(BODIES.values())) {
            if (body.isRemoved()) continue;
            drive(w, body);
        }
    }

    /** Makes the set of bodies match who should have one. */
    private static void reconcile(RoeWorld w) {
        BODIES.values().removeIf(e -> e.isRemoved());
        java.util.Set<String> wanted = new java.util.HashSet<>();
        for (Settlement s : w.state().settlements()) {
            if (!w.state().embodied(s.id())) continue;
            ServerLevel level = w.level(s.centre());
            if (level == null) continue;
            for (Character c : w.state().residents(s.id())) {
                wanted.add(c.id());
                if (body(c.id()) == null) spawn(w, level, s, c, false);
            }
            for (Character c : Survivors.waiting(w.state(), s.id())) {
                wanted.add(c.id());
                if (body(c.id()) == null) spawn(w, level, s, c, true);
            }
        }
        for (PersonEntity body : List.copyOf(BODIES.values())) {
            if (wanted.contains(body.characterId())) continue;
            Character c = w.state().character(body.characterId());
            if (c != null && c.status() == Character.Status.LEFT && body.task.phase != BodyTask.Phase.LEAVING) {
                walkAway(w, body);
                continue;
            }
            if (outOfSight(body.level(), body.blockPosition())) {
                body.discard();
                BODIES.remove(body.characterId());
            }
        }
    }

    private static void spawn(RoeWorld w, ServerLevel level, Settlement s, Character c, boolean arriving) {
        BlockPos centre = RoeWorld.pos(s.centre());
        BlockPos at;
        if (!arriving && outOfSight(level, centre)) {
            at = ground(level, centre.getX() + (c.id().hashCode() % 5), centre.getZ() + 3 + (c.id().hashCode() >>> 3) % 4);
        } else {
            at = edgeOfSight(level, centre, c.id().hashCode());
            if (at == null) return;
        }
        if (!level.hasChunkAt(at)) return;
        PersonEntity body = ModEntities.PERSON.create(level, EntitySpawnReason.EVENT);
        if (body == null) return;
        body.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        body.bind(c);
        if (arriving || !outOfSight(level, centre)) {
            body.task.phase = arriving ? BodyTask.Phase.ARRIVING : BodyTask.Phase.NONE;
            body.task.target = campfire(level, centre);
        }
        level.addFreshEntity(body);
        BODIES.put(c.id(), body);
    }

    /** True if no player is close enough to see this spot. */
    public static boolean outOfSight(net.minecraft.world.level.Level level, BlockPos pos) {
        for (Player p : level.players()) {
            if (p.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) < McTuning.OUT_OF_SIGHT_BLOCKS * McTuning.OUT_OF_SIGHT_BLOCKS) return false;
        }
        return true;
    }

    /** A loaded ground spot out at arrival distance from the centre that no player can see, or null. */
    public static BlockPos edgeOfSight(ServerLevel level, BlockPos centre, int seed) {
        for (int i = 0; i < 16; i++) {
            double angle = (seed + i * 2.399) % (Math.PI * 2);
            int x = centre.getX() + (int) (Math.cos(angle) * McTuning.ARRIVAL_DISTANCE_BLOCKS);
            int z = centre.getZ() + (int) (Math.sin(angle) * McTuning.ARRIVAL_DISTANCE_BLOCKS);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
            BlockPos p = ground(level, x, z);
            if (outOfSight(level, p)) return p;
        }
        return null;
    }

    public static BlockPos ground(ServerLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    private static BlockPos campfire(ServerLevel level, BlockPos centre) {
        return ground(level, centre.getX() + 2, centre.getZ() + 2);
    }

    private static void walkAway(RoeWorld w, PersonEntity body) {
        ServerLevel level = (ServerLevel) body.level();
        BlockPos away = edgeOfSight(level, body.blockPosition(), body.getId());
        body.task.reset();
        body.task.phase = BodyTask.Phase.LEAVING;
        body.task.target = away == null ? body.blockPosition().offset(McTuning.ARRIVAL_DISTANCE_BLOCKS, 0, 0) : away;
        body.doing = "leaving";
    }

    // ---------------------------------------------------------------- driving

    private static void drive(RoeWorld w, PersonEntity body) {
        Character c = w.state().character(body.characterId());
        if (c == null) return;
        BodyTask t = body.task;
        ServerLevel level = (ServerLevel) body.level();
        switch (t.phase) {
            case ARRIVING -> {
                body.doing = "coming to your fire";
                if (walk(body, t.target)) {
                    t.phase = BodyTask.Phase.WAITING;
                    say(level, body, "We saw your fire from the hills. Will you let us stay? "
                            + "(Right-click to take them in, sneak and right-click to send them away.)");
                }
            }
            case WAITING -> {
                body.doing = "waiting to be taken in";
                if (!w.state().tag(Ref.character(c.id()), Survivors.BOUND_FOR).isEmpty()) return;
                t.reset();
            }
            case LEAVING -> {
                if (walk(body, t.target) || outOfSight(level, body.blockPosition()) && t.ticks++ > 100) {
                    if (outOfSight(level, body.blockPosition())) {
                        body.discard();
                        BODIES.remove(body.characterId());
                    }
                }
            }
            default -> work(w, body, c, level);
        }
    }

    private static void work(RoeWorld w, PersonEntity body, Character c, ServerLevel level) {
        BodyTask t = body.task;
        if (!w.state().embodied(c.settlementId()) || c.profession().isEmpty() || c.profession().equals(Professions.FIGHTER)) {
            body.doing = c.profession().equals(Professions.FIGHTER) ? "standing watch" : "";
            if (t.phase == BodyTask.Phase.NONE && w.ticks() % 200 == body.getId() % 200) idleNear(body, w, c);
            return;
        }
        Jobs jobs = CampRules.jobs(w.sim());
        WorkOrder current = Jobs.currentOrder(w.state(), c.id());
        if (current == null && t.phase != BodyTask.Phase.NONE) t.reset();
        if (current == null) {
            if (w.ticks() < t.nextLook) return;
            t.nextLook = w.ticks() + 100;
            WorkOrder next = jobs.nextOrder(w.sim(), c.id());
            if (next.isIdle()) {
                body.doing = "";
                idleNear(body, w, c);
                return;
            }
            if (jobs.begin(w.sim(), c.id(), next) == null) return;
            current = next;
        }
        if (t.order == null || !t.order.equals(current)) {
            t.reset();
            t.order = current;
            t.phase = BodyTask.Phase.TO_WORK;
            t.target = workSpot(w, level, c, current);
            body.doing = describe(current);
            if (current.step().equals(Jobs.STEP_PLANT)) body.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WHEAT_SEEDS));
            else if (current.step().equals(Jobs.STEP_BUILD)) body.setItemSlot(EquipmentSlot.MAINHAND, stackOf(current.takes().get(0).item()));
            else body.setItemSlot(EquipmentSlot.MAINHAND, toolFor(current));
        }
        switch (t.phase) {
            case TO_WORK -> {
                if (walk(body, t.target)) { t.phase = BodyTask.Phase.WORKING; t.ticks = 0; }
            }
            case WORKING -> {
                t.ticks++;
                if (t.ticks % 20 == 0) body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
                double due = w.state().stat(Ref.character(c.id()), Jobs.DUE_HOUR);
                if (t.ticks >= McTuning.WORK_ACTION_TICKS && w.state().hour() >= due) {
                    onWorkDone(w, level, c, current);
                    if (current.gives().isEmpty()) finish(w, body, c, current);
                    else {
                        t.phase = BodyTask.Phase.TO_STORE;
                        t.target = store(w, level, c);
                        body.setItemSlot(EquipmentSlot.MAINHAND, stackOf(current.gives().get(0).item()));
                    }
                }
            }
            case TO_STORE -> {
                if (walk(body, t.target)) finish(w, body, c, current);
            }
            default -> { }
        }
    }

    private static void finish(RoeWorld w, PersonEntity body, Character c, WorkOrder order) {
        CampRules.jobs(w.sim()).complete(w.sim(), c.id(), order);
        body.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        body.task.reset();
        body.doing = "";
    }

    /** Visible results of a job step that are not stockpile goods: crops in the field. */
    private static void onWorkDone(RoeWorld w, ServerLevel level, Character c, WorkOrder order) {
        List<BlockPos> field = field(w, level, c);
        if (field.isEmpty()) return;
        if (order.step().equals(Jobs.STEP_PLANT)) {
            int n = order.takes().get(0).count();
            for (int i = 0; i < field.size() && i < n; i++) {
                BlockPos p = field.get(i).above();
                if (level.getBlockState(p).canBeReplaced()) level.setBlockAndUpdate(p, Blocks.WHEAT.defaultBlockState());
            }
        } else if (order.step().equals(Jobs.STEP_HARVEST)) {
            for (BlockPos f : field) {
                BlockPos p = f.above();
                if (level.getBlockState(p).is(Blocks.WHEAT)) level.destroyBlock(p, false);
            }
        }
    }

    /** Farmland of the farm this farmer tends. */
    public static List<BlockPos> fieldOf(RoeWorld w, ServerLevel level, Character c) {
        return field(w, level, c);
    }

    /** Farmland of the farm this farmer tends, or empty if the camp has no finished farm for them. */
    static List<BlockPos> field(RoeWorld w, ServerLevel level, Character c) {
        List<String> farms = Sites.finished(w.state(), c.settlementId(), "farm");
        if (farms.isEmpty()) return List.of();
        List<Character> farmers = w.state().residents(c.settlementId()).stream()
                .filter(p -> p.profession().equals(Professions.FARMER)).toList();
        int index = Math.max(0, farmers.indexOf(c)) % farms.size();
        Place origin = Sites.origin(w.state(), farms.get(index));
        Blueprint bp = Blueprints.get("farm");
        if (origin == null || bp == null) return List.of();
        List<BlockPos> out = new ArrayList<>();
        for (Blueprint.Placement p : bp.placements()) {
            if (p.blockState().equals("minecraft:farmland")) out.add(Sites.at(origin, p));
        }
        return out;
    }

    private static BlockPos workSpot(RoeWorld w, ServerLevel level, Character c, WorkOrder order) {
        Settlement s = w.state().settlement(c.settlementId());
        BlockPos centre = RoeWorld.pos(s.centre());
        switch (order.step()) {
            case Jobs.STEP_PLANT, Jobs.STEP_HARVEST -> {
                List<BlockPos> field = field(w, level, c);
                if (!field.isEmpty()) return field.get(field.size() / 2).above();
                return ground(level, centre.getX() - 8, centre.getZ() + 6);
            }
            case Jobs.STEP_BUILD -> {
                Place origin = Sites.origin(w.state(), order.siteId());
                if (origin != null) return ground(level, origin.x() - 1, origin.z() - 1);
                return centre;
            }
            default -> {
                double angle = (c.id().hashCode() & 0xff) / 256.0 * Math.PI * 2;
                int r = (int) (s.claimRadius() * 0.8);
                return ground(level, centre.getX() + (int) (Math.cos(angle) * r), centre.getZ() + (int) (Math.sin(angle) * r));
            }
        }
    }

    private static BlockPos store(RoeWorld w, ServerLevel level, Character c) {
        Settlement s = w.state().settlement(c.settlementId());
        List<String> seals = io.github.bl4z3es.riseofempires.core.Wire.unlist(w.state().tag(Ref.settlement(s.id()), Stockpiles.SEALS));
        if (!seals.isEmpty()) {
            String seal = seals.get(0);
            String[] xyz = seal.substring(seal.lastIndexOf('@') + 1).split(",");
            return new BlockPos(Integer.parseInt(xyz[0]) + 1, Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
        }
        return campfire(level, RoeWorld.pos(s.centre()));
    }

    /** Walks towards a spot. True once there, or once it is plain the spot cannot be reached. */
    private static boolean walk(PersonEntity body, BlockPos target) {
        if (target == null) return true;
        if (body.distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5) < McTuning.REACH_BLOCKS * McTuning.REACH_BLOCKS) {
            body.getNavigation().stop();
            return true;
        }
        if (body.getTarget() != null) return false;
        if (body.getNavigation().isDone()) {
            body.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, McTuning.WALK_SPEED * 1.6);
        }
        if (++body.task.stuck > McTuning.STUCK_TICKS) {
            body.task.stuck = 0;
            return true;
        }
        return false;
    }

    private static void idleNear(PersonEntity body, RoeWorld w, Character c) {
        Settlement s = w.state().settlement(c.settlementId());
        if (s == null || !body.getNavigation().isDone()) return;
        BlockPos centre = RoeWorld.pos(s.centre());
        int dx = body.getRandom().nextInt(9) - 4, dz = body.getRandom().nextInt(9) - 4;
        BlockPos p = ground((ServerLevel) body.level(), centre.getX() + 2 + dx, centre.getZ() + 4 + dz);
        body.getNavigation().moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, McTuning.WALK_SPEED);
    }

    private static String describe(WorkOrder o) {
        return switch (o.step()) {
            case Jobs.STEP_PLANT -> "planting wheat";
            case Jobs.STEP_HARVEST -> "harvesting wheat";
            case Jobs.STEP_GATHER -> "gathering " + Food.itemName(o.gives().get(0).item());
            case Jobs.STEP_BUILD -> "building";
            default -> o.step();
        };
    }

    private static ItemStack toolFor(WorkOrder o) {
        if (o.step().equals(Jobs.STEP_HARVEST)) return new ItemStack(Items.STONE_HOE);
        if (o.step().equals(Jobs.STEP_GATHER)) {
            return o.gives().get(0).item().equals(Food.OAK_LOG) ? new ItemStack(Items.STONE_AXE) : new ItemStack(Items.STONE_PICKAXE);
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack stackOf(String item) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(item)));
    }

    // ---------------------------------------------------------------- the player's side

    /**
     * Right-click: a survivor asking to stay is taken in (sneak: sent away). A resident tells
     * you who they are; sneak and right-click gives them the next job (task M1-14).
     */
    static void interact(RoeWorld w, PersonEntity body, Character c, Player player) {
        String bound = w.state().tag(Ref.character(c.id()), Survivors.BOUND_FOR);
        if (!bound.isEmpty()) {
            if (player.isShiftKeyDown()) {
                CampRules.survivors(w.sim()).turnAway(w.sim(), bound, c.id());
                say((ServerLevel) body.level(), body, "Then we'll take our chances in the dark.");
                walkAway(w, body);
            } else {
                Event joined = CampRules.survivors(w.sim()).admit(w.sim(), bound, c.id());
                if (joined != null) {
                    body.setSettlement(bound);
                    body.task.reset();
                    say((ServerLevel) body.level(), body, "Thank you. I'm " + c.name() + ". Tell me what to do and I'll do it.");
                }
            }
            return;
        }
        if (player.isShiftKeyDown() && c.alive() && !c.settlementId().isEmpty()) {
            List<String> jobs = new ArrayList<>(Jobs.ACT_ONE_JOBS);
            jobs.add("");
            int i = jobs.indexOf(c.profession());
            String next = jobs.get((i + 1) % jobs.size());
            CampRules.jobs(w.sim()).assign(w.sim(), c.id(), next, 0);
            body.task.reset();
            body.setItemSlot(EquipmentSlot.MAINHAND, next.equals(Professions.FIGHTER)
                    ? new ItemStack(Items.WOODEN_SWORD) : ItemStack.EMPTY);
            player.sendSystemMessage(Component.literal(c.name() + " is now " + (next.isEmpty() ? "without work" : "a " + next) + ".")
                    .withStyle(ChatFormatting.GOLD));
            return;
        }
        player.sendSystemMessage(body.describe(w, w.state().character(c.id())));
    }

    /** A body died: the character dies in the simulation, with whoever struck the blow. */
    static void onDeath(RoeWorld w, PersonEntity body, DamageSource source) {
        Character c = w.state().character(body.characterId());
        BODIES.remove(body.characterId());
        if (c == null || !c.alive()) return;
        List<Ref> by = new ArrayList<>();
        String killer = "the wilds";
        long cause = 0;
        var attacker = source.getEntity();
        if (attacker instanceof ServerPlayer p) {
            by.add(Ref.player(p.getUUID().toString()));
            killer = p.getName().getString();
        } else if (attacker instanceof BarbarianEntity b) {
            killer = "a raider";
            cause = (long) w.state().stat(Raids.ref(b.raidId), Raids.LAST_EVENT);
            by.add(Raids.ref(b.raidId));
        } else if (attacker != null) {
            killer = attacker.getName().getString();
        }
        People.die(w.sim(), c.id(), c.name() + " was killed by " + killer + ".", by, cause);
    }

    /** Says a line aloud to players near the body. */
    public static void say(ServerLevel level, PersonEntity body, String line) {
        Component msg = Component.literal("<" + body.getName().getString() + "> ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(line).withStyle(ChatFormatting.WHITE));
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(body) < 32 * 32) p.sendSystemMessage(msg);
        }
    }

    /** All bodies of one settlement's residents that are in the world. */
    public static List<PersonEntity> of(RoeWorld w, String settlementId) {
        List<PersonEntity> out = new ArrayList<>();
        for (Character c : w.state().residents(settlementId)) {
            PersonEntity b = body(c.id());
            if (b != null) out.add(b);
        }
        return out;
    }

    /** Test hook: crop ages for a farmer's field (unused in play). */
    static int age(ServerLevel level, BlockPos p) {
        var st = level.getBlockState(p);
        return st.getBlock() instanceof CropBlock crop ? crop.getAge(st) : -1;
    }
}
