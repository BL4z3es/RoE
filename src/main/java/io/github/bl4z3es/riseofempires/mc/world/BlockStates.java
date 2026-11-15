package io.github.bl4z3es.riseofempires.mc.world;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

/** Reads block states written as text, for example {@code minecraft:oak_stairs[facing=east]}. */
public final class BlockStates {
    private BlockStates() {}

    private static final Map<String, BlockState> CACHE = new HashMap<>();

    public static BlockState parse(String text) {
        return CACHE.computeIfAbsent(text, t -> {
            try {
                return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, t, false).blockState();
            } catch (CommandSyntaxException e) {
                throw new IllegalArgumentException("Not a block state: " + t, e);
            }
        });
    }

    public static String write(BlockState state) {
        return BlockStateParser.serialize(state);
    }
}
