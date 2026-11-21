package io.github.bl4z3es.riseofempires.mc.opening;

import io.github.bl4z3es.riseofempires.core.model.SimState;
import io.github.bl4z3es.riseofempires.core.sim.Founding;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import io.github.bl4z3es.riseofempires.mc.block.CharterItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Notices the first time each player holds a Charter and writes it to the Ledger, once per player. */
public final class CharterWatcher implements RoeServer.Module {
	@Override
	public void tick(RoeServer roe, long now) {
		if (CharterItem.ITEM == null) return;
		SimState state = roe.state();
		for (ServerPlayer player : roe.server().getPlayerList().getPlayers()) {
			String uuid = player.getUUID().toString();
			if (state.fact("charter." + uuid).isPresent()) continue;
			if (!player.getInventory().contains(s -> s.is(CharterItem.ITEM))) continue;
			Founding.charterTaken(state, uuid, RoeServer.place((ServerLevel) player.level(), player.blockPosition()), now);
		}
	}
}
