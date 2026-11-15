package io.github.bl4z3es.riseofempires.mc.world;

import io.github.bl4z3es.riseofempires.RiseOfEmpires;
import io.github.bl4z3es.riseofempires.core.Rng;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Nation;
import io.github.bl4z3es.riseofempires.core.sim.Effects;
import io.github.bl4z3es.riseofempires.core.sim.camp.Blueprint;
import io.github.bl4z3es.riseofempires.mc.McTuning;
import io.github.bl4z3es.riseofempires.mc.block.ModItems;
import io.github.bl4z3es.riseofempires.mc.entity.BarbarianEntity;
import io.github.bl4z3es.riseofempires.mc.entity.ModEntities;
import java.io.BufferedReader;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The Hollow Ruin (task M1-09): one per world, {@link McTuning#RUIN_DISTANCE_BLOCKS} from spawn
 * in a direction set by the world seed. It is put up when its chunk is first loaded, before any
 * player can see it (law L1), from {@code data/roe/ruin/hollow_ruin.txt}. Its chest holds the
 * Charter. A few weak raiders camp there until the first camp is founded.
 */
public final class HollowRuin {
    private HollowRuin() {}

    public static final String KNOWN = "hollow_ruin_known";
    public static final String STANDS = "hollow_ruin_stands";
    /** World tags: the ruin's chunk and whether it has been built. */
    public static final String CHUNK_X = "ruin.chunk_x", CHUNK_Z = "ruin.chunk_z", BUILT = "ruin.built";

    public static void register() {
        ServerChunkEvents.CHUNK_LOAD.register(HollowRuin::onChunkLoad);
    }

    /** Chooses where the ruin stands, once per world. Called at server start. */
    public static void choose(RoeWorld w) {
        if (!w.state().tag(Ref.THE_WORLD, CHUNK_X).isEmpty()) return;
        BlockPos spawn = w.overworld().getRespawnData().pos();
        double angle = Rng.of(w.state().seed(), 0, "hollow_ruin").nextDouble() * Math.PI * 2;
        int x = spawn.getX() + (int) (Math.cos(angle) * McTuning.RUIN_DISTANCE_BLOCKS);
        int z = spawn.getZ() + (int) (Math.sin(angle) * McTuning.RUIN_DISTANCE_BLOCKS);
        ChunkPos chunk = new ChunkPos(new BlockPos(x, 0, z));
        w.sim().emit(EventDraft.of(KNOWN).actor(Ref.THE_WORLD)
                .effect(Effects.setTag(Ref.THE_WORLD, CHUNK_X, String.valueOf(chunk.x)))
                .effect(Effects.setTag(Ref.THE_WORLD, CHUNK_Z, String.valueOf(chunk.z)))
                .summary("Long before you came, a waystation of the old empire fell to ruin "
                        + McTuning.RUIN_DISTANCE_BLOCKS + " paces from where you woke. Its keeper hid the Charter there."));
    }

    /** Where the ruin's north-west corner is in a chunk: two blocks in, so it fits inside. */
    static BlockPos corner(LevelChunk chunk, ServerLevel level) {
        int x = chunk.getPos().getMinBlockX() + 2, z = chunk.getPos().getMinBlockZ() + 2;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + 5, z + 5) - 1;
        return new BlockPos(x, y, z);
    }

    private static void onChunkLoad(ServerLevel level, LevelChunk chunk) {
        RoeWorld w = RoeWorld.get(level.getServer());
        if (w == null || level != w.overworld()) return;
        String cx = w.state().tag(Ref.THE_WORLD, CHUNK_X);
        if (cx.isEmpty() || chunk.getPos().x != Integer.parseInt(cx)
                || chunk.getPos().z != Integer.parseInt(w.state().tag(Ref.THE_WORLD, CHUNK_Z))) return;
        BlockPos corner = corner(chunk, level);
        if (w.state().tag(Ref.THE_WORLD, BUILT).isEmpty()) build(w, level, corner);
        if (noCampYet(w)) guard(level, corner);
    }

    private static boolean noCampYet(RoeWorld w) {
        for (Nation n : w.state().nations()) if (n.playerRuled()) return false;
        return true;
    }

    /** Builds the ruin with its north-west bottom corner here. Also used by the game test. */
    public static void buildAt(RoeWorld w, ServerLevel level, BlockPos corner) {
        build(w, level, corner);
    }

    private static void build(RoeWorld w, ServerLevel level, BlockPos corner) {
        Blueprint ruin = load(level);
        if (ruin == null) return;
        for (Blueprint.Placement p : ruin.placements()) {
            if (p.keep()) continue;
            BlockPos pos = Sites.at(new Place("", corner.getX(), corner.getY(), corner.getZ()), p);
            level.setBlock(pos, BlockStates.parse(p.blockState()), Block.UPDATE_CLIENTS);
            if (p.y() == 0 && !p.air()) {
                // Footings: fill any gap under the floor so the ruin does not float.
                for (int d = 1; d <= 4 && level.getBlockState(pos.below(d)).canBeReplaced(); d++) {
                    level.setBlock(pos.below(d), Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
            if (p.blockState().startsWith("minecraft:chest") && level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
                chest.setItem(13, new ItemStack(ModItems.CHARTER));
                chest.setItem(3, new ItemStack(Items.BREAD, McTuning.RUIN_BREAD));
                chest.setItem(21, new ItemStack(Items.WHEAT_SEEDS, McTuning.RUIN_SEEDS));
            }
        }
        Event known = w.sim().ledger().latest(KNOWN);
        w.sim().emit(EventDraft.of(STANDS).actor(Ref.THE_WORLD).causedBy(known)
                .at(RoeWorld.place(level, corner))
                .effect(Effects.setTag(Ref.THE_WORLD, BUILT, "1"))
                .summary("The Hollow Ruin stands at " + corner.getX() + " " + corner.getY() + " " + corner.getZ() + "."));
        RiseOfEmpires.LOG.info("[riseofempires] Hollow Ruin placed at {}", corner);
    }

    private static void guard(ServerLevel level, BlockPos corner) {
        for (int i = 0; i < McTuning.RUIN_GUARDS; i++) {
            BarbarianEntity b = ModEntities.BARBARIAN.create(level, EntitySpawnReason.EVENT);
            if (b == null) continue;
            b.snapTo(corner.getX() + 3.5 + i * 2, corner.getY() + 1, corner.getZ() + 5.5);
            level.addFreshEntity(b);
        }
    }

    private static Blueprint load(ServerLevel level) {
        var res = level.getServer().getResourceManager().getResource(Identifier.fromNamespaceAndPath("roe", "ruin/hollow_ruin.txt"));
        if (res.isEmpty()) {
            RiseOfEmpires.LOG.error("[riseofempires] data/roe/ruin/hollow_ruin.txt is missing");
            return null;
        }
        try (BufferedReader r = res.get().openAsReader()) {
            return Blueprint.parse("hollow_ruin", r.lines().collect(Collectors.joining("\n")));
        } catch (Exception e) {
            RiseOfEmpires.LOG.error("[riseofempires] The Hollow Ruin could not be read: {}", e.getMessage());
            return null;
        }
    }
}
