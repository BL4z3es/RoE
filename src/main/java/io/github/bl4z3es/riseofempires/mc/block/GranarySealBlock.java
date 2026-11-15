package io.github.bl4z3es.riseofempires.mc.block;

import io.github.bl4z3es.riseofempires.mc.world.Stockpiles;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Marks a storage building (task M1-12). Every chest and barrel within
 * {@link io.github.bl4z3es.riseofempires.mc.McTuning#GRANARY_RADIUS} blocks of a Seal inside a settlement's claim is part of
 * that settlement's stockpile. The block itself holds nothing; see {@code mc.world.Stockpiles}.
 */
public class GranarySealBlock extends Block {
    public GranarySealBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server && !oldState.is(this)) Stockpiles.sealPlaced(server, pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        Stockpiles.sealRemoved(level, pos);
    }
}
