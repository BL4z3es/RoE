package io.github.bl4z3es.riseofempires.mc.world;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.sim.camp.ActOne;
import io.github.bl4z3es.riseofempires.core.sim.camp.Advice;
import io.github.bl4z3es.riseofempires.core.sim.camp.Farming;
import io.github.bl4z3es.riseofempires.core.sim.camp.Guidance;
import io.github.bl4z3es.riseofempires.core.sim.camp.Jobs;
import io.github.bl4z3es.riseofempires.core.sim.camp.Needs;
import io.github.bl4z3es.riseofempires.core.sim.camp.Raids;
import io.github.bl4z3es.riseofempires.core.sim.Calendar;
import io.github.bl4z3es.riseofempires.mc.McTuning;
import io.github.bl4z3es.riseofempires.mc.entity.Bodies;
import io.github.bl4z3es.riseofempires.mc.entity.PersonEntity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * Small jobs that keep the world and the rules telling the same story while a camp is watched:
 * counting beds under a roof, showing crop growth on real wheat, survivors saying what the camp
 * needs (task M1-22), and telling nearby players when something important happens.
 */
public final class Upkeep {
    private Upkeep() {}

    private static final int BED_TICKS = 200;
    private static final int CROP_TICKS = 100;
    /** Event types announced to players at the camp, in gold. */
    private static final List<String> ANNOUNCE = List.of(Raids.ANNOUNCED, Raids.RESOLVED, ActOne.WINTER_WARNING,
            ActOne.WINTER_CAME, ActOne.SURVIVED, ActOne.CAMP_LOST, io.github.bl4z3es.riseofempires.core.sim.camp.People.DEATH,
            io.github.bl4z3es.riseofempires.core.sim.camp.People.LEFT, Needs.FOOD_RAN_OUT, Needs.FELL_ILL, io.github.bl4z3es.riseofempires.core.sim.camp.BuildSites.FINISHED,
            io.github.bl4z3es.riseofempires.core.sim.camp.Survivors.SET_OUT);
    private static final Map<String, String> LAST_TOPIC = new HashMap<>();

    public static void register() {
        RoeWorld.onTick(Upkeep::tick);
    }

    /** Hooks the Ledger so players hear about important events. Called once per world. */
    public static void listen(RoeWorld w) {
        w.sim().ledger().onAppend(e -> {
            if (ANNOUNCE.contains(e.type())) announce(w, e);
        });
    }

    private static void tick(RoeWorld w) {
        long t = w.ticks();
        for (Settlement s : List.copyOf(w.state().settlements())) {
            if (!w.state().embodied(s.id())) continue;
            ServerLevel level = w.level(s.centre());
            if (level == null) continue;
            if (t % BED_TICKS == 0) countBeds(w, level, s);
            if (t % CROP_TICKS == 0) growCrops(w, level, s);
            if (t % McTuning.ADVICE_TICKS == 0) advise(w, level, s);
        }
    }

    /** Beds are looked for this far below and above the camp centre. */
    private static final int BED_SCAN_DOWN = 8, BED_SCAN_UP = 12;

    private static void countBeds(RoeWorld w, ServerLevel level, Settlement s) {
        int beds = 0;
        BlockPos c = RoeWorld.pos(s.centre());
        int r = s.claimRadius();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = c.getX() - r; x <= c.getX() + r; x++) {
            for (int z = c.getZ() - r; z <= c.getZ() + r; z++) {
                int dx = x - c.getX(), dz = z - c.getZ();
                if (dx * dx + dz * dz > r * r || !level.hasChunkAt(p.set(x, c.getY(), z))) continue;
                for (int y = c.getY() - BED_SCAN_DOWN; y <= c.getY() + BED_SCAN_UP; y++) {
                    var st = level.getBlockState(p.set(x, y, z));
                    if (st.getBlock() instanceof BedBlock && st.getValue(BedBlock.PART) == BedPart.HEAD
                            && !level.canSeeSky(p.above())) beds++;
                }
            }
        }
        Needs.countBeds(w.sim(), s.id(), beds, 0);
    }

    /** Wheat in a farmer's field shows how far the rules say it has grown. */
    private static void growCrops(RoeWorld w, ServerLevel level, Settlement s) {
        for (Character c : w.state().residents(s.id())) {
            Ref r = Ref.character(c.id());
            if (w.state().stat(r, Jobs.CROPS) <= 0) continue;
            double grown = Farming.growth((long) w.state().stat(r, Jobs.PLANTED_HOUR), w.state().hour(), true)
                    / Balance.CROP_GROWTH_HOURS;
            int age = (int) Math.min(CropBlock.MAX_AGE, Math.floor(grown * CropBlock.MAX_AGE));
            for (BlockPos f : Bodies.fieldOf(w, level, c)) {
                BlockPos p = f.above();
                var st = level.getBlockState(p);
                if (st.is(Blocks.WHEAT) && ((CropBlock) Blocks.WHEAT).getAge(st) != age) {
                    level.setBlock(p, ((CropBlock) Blocks.WHEAT).getStateForAge(age), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private static void advise(RoeWorld w, ServerLevel level, Settlement s) {
        List<Advice> advice = Guidance.advise(w.sim(), s.id());
        if (advice.isEmpty()) return;
        Advice top = advice.get(0);
        String key = top.topic() + "|" + top.text();
        if (key.equals(LAST_TOPIC.get(s.id())) && top.priority() < Guidance.PRIORITY_RAID) return;
        LAST_TOPIC.put(s.id(), key);
        PersonEntity body = Bodies.body(top.speakerId());
        if (body != null) Bodies.say(level, body, top.text());
    }

    private static void announce(RoeWorld w, Event e) {
        Settlement s = null;
        for (Ref a : e.actors()) if (a.kind().equals(Ref.SETTLEMENT)) s = w.state().settlement(a.id());
        if (s == null) for (Ref a : e.actors()) {
            Character c = w.state().character(a.id());
            if (c != null) s = w.state().settlement(c.settlementId());
        }
        if (s == null) return;
        ServerLevel level = w.level(s.centre());
        if (level == null) return;
        Component msg = Component.literal(e.summary()).withStyle(ChatFormatting.GOLD)
                .append(Component.literal("  (/roe why " + e.id() + ")").withStyle(ChatFormatting.DARK_GRAY));
        double reach = s.claimRadius() + Balance.EMBODIED_RADIUS_BLOCKS * 2;
        for (ServerPlayer p : level.players()) {
            double dx = p.getX() - s.centre().x(), dz = p.getZ() - s.centre().z();
            boolean owner = w.state().nation(s.nationId()) != null
                    && w.state().nation(s.nationId()).playerId().equals(p.getUUID().toString());
            if (owner || dx * dx + dz * dz < reach * reach) p.sendSystemMessage(msg);
        }
    }
}
