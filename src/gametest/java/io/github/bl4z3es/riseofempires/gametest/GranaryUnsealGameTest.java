package io.github.bl4z3es.riseofempires.gametest;

import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Task M1-24: breaking the Granary Seal unseals the granary and clears the counted store. */
public final class GranaryUnsealGameTest {

	@GameTest
	public void breakSealUnsealsGranaryAndClearsStockpile(GameTestHelper helper) {
		RoeServer roe = RoeServer.get().orElseThrow();
		SimState s = roe.state();
		String player = UUID.randomUUID().toString();
		BlockPos sealPos = new BlockPos(1, 1, 1);
		BlockPos chestPos = new BlockPos(3, 1, 1);
		Place centre = RoeServer.place(helper.getLevel(), helper.absolutePos(sealPos));
		Founding.found(s, player, "Granary Test", centre, roe.now()).orElseThrow();
		long sid = s.settlementFoundedBy(player).orElseThrow();

		helper.setBlock(sealPos, StockpileBlocks.GRANARY_SEAL);
		helper.setBlock(chestPos, Blocks.CHEST);
		Stores.sealGranary(s, sid, centre, player, roe.now());
		helper.assertTrue(s.settlement(sid).orElseThrow().granary() != null, "granary was not sealed");

		ChestBlockEntity chest = helper.getBlockEntity(chestPos, ChestBlockEntity.class);
		chest.setItem(0, new ItemStack(Items.BREAD, 10));
		StockpileSync.recountNow(roe, s.settlement(sid).orElseThrow());
		helper.assertTrue(s.stockpile(sid).total("minecraft:bread") == 10, "expected 10 bread in stockpile");

		// The real player-breaking path: level.destroyBlock calls the player's gameMode, which calls playerWillDestroy.
		ServerPlayer breaker = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(helper.getLevel().destroyBlock(helper.absolutePos(sealPos), true, breaker),
				"the mock player could not break the seal");

		helper.succeedWhen(() -> {
			// The granary is unsealed
			helper.assertTrue(s.settlement(sid).orElseThrow().granary() == null, "granary was not unsealed");
			// Stockpile no longer counts those chests
			helper.assertTrue(s.stockpile(sid).totals().isEmpty(), "stockpile still holds items after unseal");
			// The chest physically still has the bread (law L4: nothing destroyed by the rule)
			helper.assertTrue(chest.getItem(0).getCount() == 10, "chest bread was destroyed, should remain");
			// The unseal event exists with the settlement as an actor
			helper.assertTrue(hasEvent(s, EventTypes.GRANARY_UNSEALED, Ref.settlement(sid)), "no granary_unsealed event");
			// Cause chain reaches the seal event
			var unsealEvent = s.ledger().latestOfType(EventTypes.GRANARY_UNSEALED).orElseThrow();
			var chain = s.ledger().causeChain(unsealEvent.id());
			helper.assertTrue(chain.stream().anyMatch(e -> e.type().equals(EventTypes.GRANARY_SEALED)), "unseal cause chain missing seal event");
			// Player attribution is tested in the unit test; mock player UUID may differ in test env.
		});
	}

	@GameTest
	public void explosionUnsealsGranaryWithoutPlayer(GameTestHelper helper) {
		RoeServer roe = RoeServer.get().orElseThrow();
		SimState s = roe.state();
		String player = UUID.randomUUID().toString();
		BlockPos sealPos = new BlockPos(1, 1, 1);
		BlockPos chestPos = new BlockPos(3, 1, 1);
		Place centre = RoeServer.place(helper.getLevel(), helper.absolutePos(sealPos));
		Founding.found(s, player, "Explosion Test", centre, roe.now()).orElseThrow();
		long sid = s.settlementFoundedBy(player).orElseThrow();

		helper.setBlock(sealPos, StockpileBlocks.GRANARY_SEAL);
		helper.setBlock(chestPos, Blocks.CHEST);
		Stores.sealGranary(s, sid, centre, player, roe.now());
		helper.assertTrue(s.settlement(sid).orElseThrow().granary() != null, "granary was not sealed");

		ChestBlockEntity chest = helper.getBlockEntity(chestPos, ChestBlockEntity.class);
		chest.setItem(0, new ItemStack(Items.WHEAT, 5));
		StockpileSync.recountNow(roe, s.settlement(sid).orElseThrow());
		helper.assertTrue(s.stockpile(sid).total("minecraft:wheat") == 5, "expected 5 wheat in stockpile");

		// Explosion removes the seal (the chest is outside the blast, so its contents remain).
		BlockPos absoluteSeal = helper.absolutePos(sealPos);
		helper.getLevel().explode(null, absoluteSeal.getX() + 0.5, absoluteSeal.getY() + 0.5,
				absoluteSeal.getZ() + 0.5, 2.0F, false, net.minecraft.world.level.Level.ExplosionInteraction.BLOCK);

		helper.succeedWhen(() -> {
			helper.assertTrue(s.settlement(sid).orElseThrow().granary() == null, "granary was not unsealed by explosion");
			helper.assertTrue(s.stockpile(sid).totals().isEmpty(), "stockpile still holds items after explosion unseal");
			helper.assertTrue(chest.getItem(0).getCount() == 5, "chest wheat was destroyed by explosion rule");
			helper.assertTrue(hasEvent(s, EventTypes.GRANARY_UNSEALED, Ref.settlement(sid)), "no granary_unsealed event for explosion");
		});
	}

	@GameTest
	public void pistonRemovesSealUnsealsGranary(GameTestHelper helper) {
		RoeServer roe = RoeServer.get().orElseThrow();
		SimState s = roe.state();
		String player = UUID.randomUUID().toString();
		BlockPos sealPos = new BlockPos(1, 1, 1);
		BlockPos chestPos = new BlockPos(3, 1, 1);
		Place centre = RoeServer.place(helper.getLevel(), helper.absolutePos(sealPos));
		Founding.found(s, player, "Piston Test", centre, roe.now()).orElseThrow();
		long sid = s.settlementFoundedBy(player).orElseThrow();

		helper.setBlock(sealPos, StockpileBlocks.GRANARY_SEAL);
		helper.setBlock(chestPos, Blocks.CHEST);
		Stores.sealGranary(s, sid, centre, player, roe.now());
		helper.assertTrue(s.settlement(sid).orElseThrow().granary() != null, "granary was not sealed");

		ChestBlockEntity chest = helper.getBlockEntity(chestPos, ChestBlockEntity.class);
		chest.setItem(0, new ItemStack(Items.BREAD, 3));
		StockpileSync.recountNow(roe, s.settlement(sid).orElseThrow());
		helper.assertTrue(s.stockpile(sid).total("minecraft:bread") == 3, "expected 3 bread in stockpile");

		// Piston pushes the seal out
		BlockPos pistonPos = sealPos.west();
		helper.setBlock(pistonPos, Blocks.PISTON.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.EAST));
		helper.setBlock(pistonPos.below(), Blocks.REDSTONE_BLOCK);
		helper.runAfterDelay(2, () -> {
			helper.getLevel().setBlockAndUpdate(pistonPos, Blocks.AIR.defaultBlockState());
		});

		helper.succeedWhen(() -> {
			helper.assertTrue(s.settlement(sid).orElseThrow().granary() == null, "granary was not unsealed by piston");
			helper.assertTrue(s.stockpile(sid).totals().isEmpty(), "stockpile still holds items after piston unseal");
			helper.assertTrue(chest.getItem(0).getCount() == 3, "chest bread was destroyed by piston rule");
			helper.assertTrue(hasEvent(s, EventTypes.GRANARY_UNSEALED, Ref.settlement(sid)), "no granary_unsealed event for piston");
		});
	}

	private static boolean hasEvent(SimState s, String type, Ref actor) {
		return s.ledger().all().stream().anyMatch(e -> e.type().equals(type) && e.actors().contains(actor));
	}
}