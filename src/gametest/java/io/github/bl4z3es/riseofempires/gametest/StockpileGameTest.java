package io.github.bl4z3es.riseofempires.gametest;

import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import io.github.bl4z3es.riseofempires.core.model.Stockpile;
import io.github.bl4z3es.riseofempires.core.sim.Founding;
import io.github.bl4z3es.riseofempires.core.sim.Stores;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import io.github.bl4z3es.riseofempires.mc.stockpile.StockpileBlocks;
import io.github.bl4z3es.riseofempires.mc.stockpile.StockpileSync;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Task M1-12: the stockpile is the real contents of the chests around a Granary Seal. */
public final class StockpileGameTest {
	private static final String WHEAT = "minecraft:wheat";

	@GameTest
	public void stockpileIsTheChests(GameTestHelper helper) {
		RoeServer roe = RoeServer.get().orElseThrow();
		SimState s = roe.state();
		long now = roe.now();
		String player = UUID.randomUUID().toString();
		BlockPos sealPos = new BlockPos(1, 1, 1);
		BlockPos chestPos = new BlockPos(3, 1, 1);
		Place centre = RoeServer.place(helper.getLevel(), helper.absolutePos(sealPos));
		Founding.found(s, player, "Granary Test", centre, now).orElseThrow();
		long sid = s.settlementFoundedBy(player).orElseThrow();

		helper.setBlock(sealPos, StockpileBlocks.GRANARY_SEAL);
		helper.setBlock(chestPos, Blocks.CHEST);
		Stores.sealGranary(s, sid, centre, player, now);
		helper.assertTrue(s.settlement(sid).orElseThrow().granary() != null, "granary was not sealed");

		ChestBlockEntity chest = helper.getBlockEntity(chestPos, ChestBlockEntity.class);
		chest.setItem(0, new ItemStack(Items.WHEAT, 5));
		StockpileSync.recountNow(roe, s.settlement(sid).orElseThrow());
		helper.assertTrue(s.stockpile(sid).total(WHEAT) == 5, "expected 5 wheat, found " + s.stockpile(sid).total(WHEAT));

		chest.removeItem(0, 2);
		long before = s.ledger().size();
		StockpileSync.recountNow(roe, s.settlement(sid).orElseThrow());
		helper.assertTrue(s.stockpile(sid).total(WHEAT) == 3, "expected 3 wheat, found " + s.stockpile(sid).total(WHEAT));
		helper.assertTrue(s.ledger().size() == before + 1, "recount did not write exactly one event");
		helper.assertTrue(s.ledger().all().get(s.ledger().all().size() - 1).type().equals(EventTypes.STOCKPILE_COUNTED),
				"no stockpile_counted event");

		// An off-camera change: one wheat was eaten. It waits as pending until the chests are updated.
		var ate = s.ledger().append(EventDraft.of(EventTypes.STOCKPILE_COUNTED, now).actor(Ref.settlement(sid))
				.cause(s.lastStockEvent(sid)).effect(Ref.settlement(sid), WHEAT, "-1"));
		Stockpile sp = s.stockpile(sid).withPendingChange(WHEAT, -1);
		s.put(sp, ate);
		s.noteStockEvent(sid, ate);
		helper.assertTrue(s.stockpile(sid).total(WHEAT) == 2, "pending change did not show in the total");

		StockpileSync.INSTANCE.tick(roe, now);
		helper.assertTrue(chest.getItem(0).getCount() == 2, "chest should hold 2 wheat, holds " + chest.getItem(0).getCount());
		helper.assertTrue(s.stockpile(sid).pending().isEmpty(), "pending was not cleared");
		helper.assertTrue(s.stockpile(sid).total(WHEAT) == 2, "total should stay 2");

		// A change the chests cannot take is applied to nothing and stays pending.
		s.put(s.stockpile(sid).withPendingChange(WHEAT, -9), ate);
		StockpileSync.INSTANCE.tick(roe, now);
		helper.assertTrue(chest.getItem(0).getCount() == 2, "an impossible change altered the chest");
		helper.assertTrue(!s.stockpile(sid).pending().isEmpty(), "an impossible change was dropped");
		helper.succeed();
	}
}
