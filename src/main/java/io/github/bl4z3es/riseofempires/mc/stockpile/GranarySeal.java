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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Granary Seal. Placed inside a camp's claim it names the chests around it as the camp's store.
 * Placing it outside a claim still works (law L4); the player is told why it does nothing.
 */
public final class GranarySeal extends Block {
	public GranarySeal(Properties properties) { super(properties); }

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
