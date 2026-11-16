package io.github.bl4z3es.riseofempires.mc.world;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Tells the mod when a chunk has finished loading and is safe to read and change.
 *
 * <p>Minecraft's own "chunk loaded" callback runs while the chunk is still being handed over.
 * Reading or changing the level from inside it makes the server thread wait for that same chunk
 * and the server freezes for good. So that callback only notes the chunk down here, and the work
 * is done at the end of a server tick, once the chunk is really in the world.
 */
public final class ArrivedChunks {
    private ArrivedChunks() {}

    /** Work to do on a chunk that has just become usable. Runs on the server thread. */
    public interface Listener {
        void arrived(RoeWorld world, ServerLevel level, LevelChunk chunk);
    }

    private record Waiting(ResourceKey<Level> dimension, long chunk) {}

    private static final List<Listener> LISTENERS = new ArrayList<>();
    private static final Set<Waiting> WAITING = new LinkedHashSet<>();

    public static void listen(Listener listener) {
        LISTENERS.add(listener);
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> WAITING.clear());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> WAITING.clear());
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk) ->
                WAITING.add(new Waiting(level.dimension(), chunk.getPos().toLong())));
        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) ->
                WAITING.remove(new Waiting(level.dimension(), chunk.getPos().toLong())));
        RoeWorld.onTick(ArrivedChunks::deliver);
    }

    /** How many chunks are noted down and not yet handed to the listeners. */
    public static int waiting() {
        return WAITING.size();
    }

    private static void deliver(RoeWorld world) {
        if (WAITING.isEmpty()) return;
        for (Iterator<Waiting> it = WAITING.iterator(); it.hasNext();) {
            Waiting w = it.next();
            ServerLevel level = world.server().getLevel(w.dimension());
            if (level == null) { it.remove(); continue; }
            // Null until the chunk is fully in the world. Never asks the server to load it.
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(w.chunk()), ChunkPos.getZ(w.chunk()));
            if (chunk == null) continue;
            it.remove();
            for (Listener l : LISTENERS) l.arrived(world, level, chunk);
        }
    }
}
