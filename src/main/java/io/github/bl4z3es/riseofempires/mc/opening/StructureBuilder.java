package io.github.bl4z3es.riseofempires.mc.opening;

import io.github.bl4z3es.riseofempires.mc.world.WorldBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Places a {@link Structure} in the world, turned by quarter turns clockwise. */
public final class StructureBuilder {
	private StructureBuilder() {}

	/** The world position of a structure block: rotated clockwise about the origin, then moved to it. */
	public static BlockPos worldPos(BlockPos origin, Structure.Block b, int turns) {
		int dx = b.dx();
		int dz = b.dz();
		for (int i = Math.floorMod(turns, 4); i > 0; i--) {
			int nx = -dz;
			dz = dx;
			dx = nx;
		}
		return origin.offset(dx, b.dy(), dz);
	}

	public static BlockState state(ServerLevel level, Structure.Block b, int turns) {
		return WorldBlocks.parse(level, b.state(), turns);
	}

	/** Places every block at once. Only for places no player can see (the ruin, off-camera). */
	public static void placeAll(ServerLevel level, BlockPos origin, Structure s, int turns) {
		for (Structure.Block b : s.blocks()) {
			level.setBlock(worldPos(origin, b, turns), state(level, b, turns), Block.UPDATE_CLIENTS);
		}
	}
}
