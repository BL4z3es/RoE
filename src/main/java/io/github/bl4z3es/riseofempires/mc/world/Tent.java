package io.github.bl4z3es.riseofempires.mc.world;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The founder's tent and campfire, raised block by block next to the lectern when a camp is
 * founded. A white canvas A-frame five wide and four deep with a bed inside, open to the south.
 */
public final class Tent {
    private Tent() {}

    /** Rows are y from the ground up; each row is z north to south; each char is x west to east. */
    private static final String[][] SHAPE = {
            {"W...W", "W...W", "W.B.W", "WWWWW"},
            {".W.W.", ".W.W.", ".WbW.", ".WWW."},
            {"..W..", "..W..", "..W..", "..W.."},
    };
    private static final int WIDTH = 5, DEPTH = 4;
    /** How far from the lectern the tent's corner may be. */
    private static final int SEARCH = 6;

    private static String block(char c) {
        return switch (c) {
            case 'W' -> "minecraft:white_wool";
            case 'B' -> "minecraft:red_bed[facing=north,part=head]";
            case 'b' -> "minecraft:red_bed[facing=north,part=foot]";
            default -> null;
        };
    }

    /** Finds level ground near the lectern and queues the tent and a campfire south of it. */
    public static void raise(ServerLevel level, BlockPos lectern, long cause) {
        BlockPos corner = findSite(level, lectern);
        List<BlockPos> order = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        for (int y = 0; y < SHAPE.length; y++) {
            for (int z = 0; z < DEPTH; z++) {
                for (int x = 0; x < WIDTH; x++) {
                    char c = SHAPE[y][z].charAt(x);
                    if (c == 'b') continue; // the head places the foot with it
                    String b = block(c);
                    if (b == null) continue;
                    order.add(corner.offset(x, y, z));
                    states.add(BlockStates.parse(b));
                }
            }
        }
        // The bed goes in as one piece: head first, then the foot one block south.
        for (int i = 0; i < order.size(); i++) {
            BlockPos p = order.get(i);
            if (!level.getBlockState(p).canBeReplaced()) continue;
            Construction.place(level, p, states.get(i), cause);
            if (states.get(i).toString().contains("red_bed")) {
                Construction.place(level, p.south(), BlockStates.parse("minecraft:red_bed[facing=north,part=foot]"), cause);
            }
        }
        BlockPos fire = ground(level, corner.getX() + 2, corner.getZ() + DEPTH + 2);
        if (level.getBlockState(fire).canBeReplaced()) {
            Construction.place(level, fire, BlockStates.parse("minecraft:campfire[lit=true]"), cause);
        }
    }

    private static BlockPos findSite(ServerLevel level, BlockPos lectern) {
        BlockPos best = null;
        int bestSpread = Integer.MAX_VALUE;
        for (int dx = -SEARCH; dx <= SEARCH; dx++) {
            for (int dz = -SEARCH; dz <= SEARCH; dz++) {
                int x0 = lectern.getX() + dx, z0 = lectern.getZ() + dz;
                if (overlapsLectern(lectern, x0, z0)) continue;
                int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
                for (int x = 0; x < WIDTH; x++) for (int z = 0; z < DEPTH + 3; z++) {
                    int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0 + x, z0 + z);
                    min = Math.min(min, h);
                    max = Math.max(max, h);
                }
                int spread = (max - min) * 100 + Math.abs(dx) + Math.abs(dz);
                if (spread < bestSpread) {
                    bestSpread = spread;
                    best = new BlockPos(x0, max, z0);
                }
            }
        }
        return best;
    }

    private static boolean overlapsLectern(BlockPos lectern, int x0, int z0) {
        return lectern.getX() >= x0 - 1 && lectern.getX() <= x0 + WIDTH
                && lectern.getZ() >= z0 - 1 && lectern.getZ() <= z0 + DEPTH + 3;
    }

    private static BlockPos ground(ServerLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }
}
