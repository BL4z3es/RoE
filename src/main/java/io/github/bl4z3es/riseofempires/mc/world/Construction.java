package io.github.bl4z3es.riseofempires.mc.world;

import io.github.bl4z3es.riseofempires.mc.McTuning;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Puts blocks up one at a time, with the sound and dust of each placement, so nothing appears
 * whole in front of a player (law L1). Used for the founding tent and for builders' work.
 * Blocks queued while the chunk is unloaded go to {@link DeferredEdits} instead.
 */
public final class Construction {
    private record Job(ServerLevel level, BlockPos pos, BlockState state, long cause) {}

    private static final Deque<Job> QUEUE = new ArrayDeque<>();

    private Construction() {}

    public static void register() {
        RoeWorld.onTick(Construction::tick);
    }

    public static void place(ServerLevel level, BlockPos pos, BlockState state, long cause) {
        QUEUE.add(new Job(level, pos.immutable(), state, cause));
    }

    /** True while blocks are still waiting to go up. */
    public static boolean busy() {
        return !QUEUE.isEmpty();
    }

    private static void tick(RoeWorld world) {
        if (QUEUE.isEmpty() || world.ticks() % McTuning.BUILD_TICKS_PER_BLOCK != 0) return;
        Job job = QUEUE.poll();
        if (!job.level().hasChunkAt(job.pos())) {
            world.deferred().queue(job.level(), job.pos(), BlockStates.write(job.state()), job.cause());
            return;
        }
        placeNow(job.level(), job.pos(), job.state());
    }

    /** Places one block with its sound and a puff of dust. */
    public static void placeNow(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_ALL);
        if (!state.isAir()) {
            var sound = state.getSoundType();
            level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, 0.8F, sound.getPitch());
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5,
                    pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
        }
    }
}
