package io.github.bl4z3es.riseofempires.mc.world;

import io.github.bl4z3es.riseofempires.core.Wire;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;

/**
 * Block changes made while nobody could see them (task M1-17, rule E6). Each is queued against
 * its chunk and applied in order once that chunk has next finished loading (see
 * {@link ArrivedChunks}). Chunks are never loaded to apply them. Saved with the world.
 */
public final class DeferredEdits {
    /** One block change. {@code cause} is the Ledger event that made it, or 0. */
    public record Edit(String dimension, int x, int y, int z, String blockState, long cause) {
        String encode() {
            return Wire.line(dimension, x, y, z, blockState, cause);
        }

        static Edit decode(String s) {
            String[] f = Wire.fields(s);
            return new Edit(f[0], Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]), f[4],
                    Long.parseLong(f[5]));
        }

        BlockPos pos() { return new BlockPos(x, y, z); }
    }

    private final Map<String, List<Edit>> byChunk = new LinkedHashMap<>();

    public static void register() {
        ArrivedChunks.listen((w, level, chunk) -> w.deferred().applyChunk(level, chunk.getPos()));
    }

    /**
     * Queues a change. If the chunk is loaded now it is applied at once, so only call this for
     * places no player can see; visible building goes through {@link Construction}.
     */
    public void queue(ServerLevel level, BlockPos pos, String blockState, long cause) {
        Edit edit = new Edit(level.dimension().identifier().toString(), pos.getX(), pos.getY(), pos.getZ(), blockState, cause);
        if (level.hasChunkAt(pos)) {
            apply(level, edit);
            return;
        }
        byChunk.computeIfAbsent(key(edit.dimension(), new ChunkPos(pos)), k -> new ArrayList<>()).add(edit);
    }

    public int pending() {
        return byChunk.values().stream().mapToInt(List::size).sum();
    }

    void applyChunk(ServerLevel level, ChunkPos chunk) {
        List<Edit> edits = byChunk.remove(key(level.dimension().identifier().toString(), chunk));
        if (edits == null) return;
        for (Edit e : edits) apply(level, e);
    }

    private static void apply(ServerLevel level, Edit e) {
        level.setBlock(e.pos(), BlockStates.parse(e.blockState()), Block.UPDATE_ALL);
    }

    private static String key(String dimension, ChunkPos pos) {
        return dimension + "@" + pos.x + "," + pos.z;
    }

    public String save() {
        StringBuilder sb = new StringBuilder();
        for (List<Edit> list : byChunk.values()) for (Edit e : list) sb.append(e.encode()).append('\n');
        return sb.toString();
    }

    public static DeferredEdits load(String text) {
        DeferredEdits d = new DeferredEdits();
        for (String line : text.split("\n")) {
            if (line.isEmpty()) continue;
            Edit e = Edit.decode(line);
            d.byChunk.computeIfAbsent(key(e.dimension(), new ChunkPos(e.pos())), k -> new ArrayList<>()).add(e);
        }
        return d;
    }
}
