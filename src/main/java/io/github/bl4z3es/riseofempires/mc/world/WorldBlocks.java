package io.github.bl4z3es.riseofempires.mc.world;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.bl4z3es.riseofempires.RiseOfEmpires;
import io.github.bl4z3es.riseofempires.core.Balance;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/** Helpers for turning blueprint text into blocks, and for "can a player see this spot". */
public final class WorldBlocks {
	private WorldBlocks() {}

	private static final Rotation[] TURNS = {Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90};

	/** Parses a block state such as {@code minecraft:oak_stairs[facing=north]}, turned clockwise. Air if unreadable. */
	public static BlockState parse(ServerLevel level, String text, int quarterTurns) {
		try {
			BlockState state = BlockStateParser.parseForBlock(level.registryAccess().lookupOrThrow(Registries.BLOCK), text, false).blockState();
			return state.rotate(TURNS[Math.floorMod(quarterTurns, 4)]);
		} catch (CommandSyntaxException e) {
			RiseOfEmpires.LOGGER.warn("Unreadable block state '{}'", text);
			return Blocks.AIR.defaultBlockState();
		}
	}

	/** True if any player is within the Embodied radius: closer than that, things must not change unseen. */
	public static boolean playerNear(ServerLevel level, BlockPos pos) {
		return level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, Balance.EMBODIED_RADIUS_BLOCKS, false) != null;
	}
}
