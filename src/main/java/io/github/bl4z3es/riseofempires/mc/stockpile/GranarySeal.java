package io.github.bl4z3es.riseofempires.mc.stockpile;

import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.sim.Founding;
import io.github.bl4z3es.riseofempires.core.sim.Stores;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.function.BiConsumer;

/**
 * The Granary Seal. Placed inside a camp's claim it names the chests around it as the camp's store.
 * Placing it outside a claim still works (law L4); the player is told why it does nothing.
 */
public final class GranarySeal extends Block {
	public static final String BROKEN = "broken";
	public static final String EXPLODED = "exploded";
	public static final String MOVED_BY_PISTON = "piston";
	public static final String REPLACED = "replaced";

	public GranarySeal(Properties properties) { super(properties); }

	/** A player break runs before the block disappears, so the event can name its cause. */
	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (level instanceof ServerLevel server) {
			RoeServer.get().ifPresent(roe -> StockpileSync.unseal(roe, server, pos, player, BROKEN));
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	/** Covers explosions, pistons, /setblock and other removals with no player to name. */
	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		RoeServer.get().ifPresent(roe -> StockpileSync.unseal(roe, level, pos, null,
				movedByPiston ? MOVED_BY_PISTON : REPLACED));
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
	}

	/** The explosion is the actor if its source is known; otherwise the removal hook records the cause. */
	@Override
	protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion,
			BiConsumer<ItemStack, BlockPos> dropConsumer) {
		RoeServer.get().ifPresent(roe -> StockpileSync.unseal(roe, level, pos,
				explosion.getIndirectSourceEntity(), EXPLODED));
		super.onExplosionHit(state, level, pos, explosion, dropConsumer);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity by, ItemStack stack) {
		super.setPlacedBy(level, pos, state, by, stack);
		if (!(level instanceof ServerLevel server) || !(by instanceof ServerPlayer player)) return;
		RoeServer.get().ifPresent(roe -> {
			Place place = RoeServer.place(server, pos);
			var claim = Founding.settlementAt(roe.state(), place);
			if (claim.isEmpty()) {
				player.sendSystemMessage(Component.translatable("message.riseofempires.granary_outside_claim"));
				return;
			}
			long sid = claim.get().id();
			Stores.sealGranary(roe.state(), sid, place, player.getStringUUID(), roe.now());
			StockpileSync.recountNow(roe, roe.state().settlement(sid).orElseThrow());
			player.sendSystemMessage(Component.translatable("message.riseofempires.granary_sealed",
					claim.get().name(), GranaryStore.RADIUS));
		});
	}
}
