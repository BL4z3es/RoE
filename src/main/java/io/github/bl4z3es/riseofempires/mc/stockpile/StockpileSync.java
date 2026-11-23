package io.github.bl4z3es.riseofempires.mc.stockpile;

import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.model.Stockpile;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.sim.Stores;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import io.github.bl4z3es.riseofempires.mc.command.RoeCommands;
import io.github.bl4z3es.riseofempires.mc.entity.CharacterEntity;
import java.util.ArrayList;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;


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

	/** Called by the seal for every removal, not only player breaking. Idempotent through the core rule. */
	public static void unseal(RoeServer roe, ServerLevel level, BlockPos pos, Entity breaker, String how) {
		Place place = RoeServer.place(level, pos);
		for (Settlement settlement : new ArrayList<>(roe.state().settlements())) {
			if (!place.equals(settlement.granary())) continue;
			reconcilePending(roe, settlement);
			Optional<Event> event = Stores.unsealGranary(
					roe.state(), settlement.id(), place, breakerRef(breaker), how, roe.now());
			event.ifPresent(e -> {
				if (breaker instanceof ServerPlayer player) {
					player.sendSystemMessage(Component.translatable(
							"message.riseofempires.granary_unsealed", settlement.name()));
				}
			});
		}
	}

	/** Try to save off-camera work in the real containers. Nothing changes if any change cannot fit. */
	private static void reconcilePending(RoeServer roe, Settlement settlement) {
		Stockpile sp = roe.state().stockpile(settlement.id());
		if (sp.pending().isEmpty()) return;
		Place place = settlement.granary();
		ServerLevel level = roe.level(place);
		if (!level.isLoaded(RoeServer.pos(place))) return;
		GranaryStore store = new GranaryStore(level, RoeServer.pos(place));
		if (!store.apply(sp.pending())) return;
		Stores.recount(roe.state(), settlement.id(), store.count(), true, roe.now()).orElseThrow(() ->
				new IllegalStateException("A successful pending flush must write its reconciliation event"));
	}

	private static Ref breakerRef(Entity breaker) {
		if (breaker instanceof ServerPlayer player) return Ref.player(player.getStringUUID());
		if (breaker instanceof CharacterEntity character) return Ref.character(character.characterId());
		return null;
	}
}
