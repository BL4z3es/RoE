package io.github.bl4z3es.riseofempires.mc.stockpile;

import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.model.Stockpile;
import io.github.bl4z3es.riseofempires.core.sim.Stores;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import io.github.bl4z3es.riseofempires.mc.command.RoeCommands;
import java.util.ArrayList;
import net.minecraft.server.level.ServerLevel;

/**
 * Keeps the stockpile and the real chests agreeing (task M1-12, rule E12): counts the chests once a
 * second and writes off-camera changes into them when the area is loaded. Never loads chunks (rule E6).
 */
public final class StockpileSync implements RoeServer.Module {
	public static final StockpileSync INSTANCE = new StockpileSync();

	private StockpileSync() {}

	public static void register() {
		StockpileBlocks.register();
		RoeServer.addModule(INSTANCE);
		RoeCommands.beforeStockpileReport = s -> RoeServer.get().ifPresent(roe -> recountNow(roe, s));
	}

	@Override
	public void tick(RoeServer roe, long now) {
		for (Settlement s : new ArrayList<>(roe.state().settlements())) {
			if (s.granary() == null) continue;
			ServerLevel level = roe.level(s.granary());
			if (!level.isLoaded(RoeServer.pos(s.granary()))) continue;
			GranaryStore store = new GranaryStore(level, RoeServer.pos(s.granary()));
			Stockpile sp = roe.state().stockpile(s.id());
			if (!sp.pending().isEmpty()) {
				// All or nothing: if the chests cannot take the changes, try again next second.
				if (store.apply(sp.pending())) Stores.recount(roe.state(), s.id(), store.count(), true, now);
				continue;
			}
			Stores.recount(roe.state(), s.id(), store.count(), false, now);
		}
	}

	/** Recounts one settlement's chests now, if its Granary Seal is in a loaded area. */
	public static void recountNow(RoeServer roe, Settlement s) {
		if (s.granary() == null) return;
		ServerLevel level = roe.level(s.granary());
		if (!level.isLoaded(RoeServer.pos(s.granary()))) return;
		Stores.recount(roe.state(), s.id(), new GranaryStore(level, RoeServer.pos(s.granary())).count(), false, roe.now());
	}
}
