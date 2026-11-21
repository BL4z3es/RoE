package io.github.bl4z3es.riseofempires.mc.opening;

import java.util.List;

/**
 * A hand-authored structure read from a data pack: block states placed around an origin.
 * {@code dy = 0} is the first air block above the ground, {@code dz} grows to the south.
 * Blocks are listed in building order: bottom layer first, and supports before what hangs on them.
 */
public record Structure(String name, List<Block> blocks) {
	/** One block of a structure, as text such as {@code minecraft:oak_stairs[facing=north]}. */
	public record Block(int dx, int dy, int dz, String state) {
		public boolean isAir() { return state.equals("minecraft:air"); }
	}
}
