package io.github.bl4z3es.riseofempires.mc.block;

import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import io.github.bl4z3es.riseofempires.core.sim.Founding;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import io.github.bl4z3es.riseofempires.mc.opening.FoundersTent;
import java.util.Optional;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Setting the Charter on a lectern founds the camp (CONTEXT.md section 2, Opening). Once per player.
 * The tent and campfire are raised beside the lectern block by block.
 */
public final class CharterLectern {
	private CharterLectern() {}

	/** PROPOSED: not in Balance yet. The camp's name; the player renames it later. */
	public static final String CAMP_NAME_FORMAT = "%s's Camp";

	public static void register() {
		UseBlockCallback.EVENT.register(CharterLectern::onUse);
	}

	private static InteractionResult onUse(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		ItemStack stack = player.getItemInHand(hand);
		if (CharterItem.ITEM == null || !stack.is(CharterItem.ITEM) || player.isSpectator()) return InteractionResult.PASS;
		BlockPos pos = hit.getBlockPos();
		if (!level.getBlockState(pos).is(Blocks.LECTERN)) return InteractionResult.PASS;
		if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
		return found(server, player, stack, pos);
	}

	/** The server side of using a Charter on the lectern at {@code pos}. Public so tests can call it directly. */
	public static InteractionResult found(ServerLevel level, Player player, ItemStack charter, BlockPos pos) {
		Optional<RoeServer> maybe = RoeServer.get();
		if (maybe.isEmpty()) return InteractionResult.PASS;
		RoeServer roe = maybe.get();
		SimState state = roe.state();
		String uuid = player.getUUID().toString();
		long now = roe.now();
		Place place = RoeServer.place(level, pos);

		Optional<Long> already = state.settlementFoundedBy(uuid);
		if (already.isPresent()) {
			String name = state.settlement(already.get()).map(s -> s.name()).orElse("a camp");
			player.sendSystemMessage(Component.translatable("riseofempires.founding.already", name));
			return InteractionResult.CONSUME;
		}
		if (state.fact("charter." + uuid).isEmpty()) {
			Founding.charterTaken(state, uuid, place, now);
		}
		String name = String.format(CAMP_NAME_FORMAT, player.getName().getString());
		Optional<Event> founded = Founding.found(state, uuid, name, place, now);
		if (founded.isEmpty()) return InteractionResult.CONSUME;

		charter.shrink(1);
		player.sendSystemMessage(Component.translatable("riseofempires.founding.founded", name));
		FoundersTent.raiseNear(level, pos);
		return InteractionResult.CONSUME;
	}
}
