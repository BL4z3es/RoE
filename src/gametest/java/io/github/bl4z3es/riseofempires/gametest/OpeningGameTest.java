package io.github.bl4z3es.riseofempires.gametest;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import io.github.bl4z3es.riseofempires.mc.block.CharterItem;
import io.github.bl4z3es.riseofempires.mc.opening.HollowRuin;
import io.github.bl4z3es.riseofempires.mc.opening.Structure;
import io.github.bl4z3es.riseofempires.mc.opening.StructureFiles;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Tasks M1-09 and M1-10: the Hollow Ruin holds a Charter, and the Charter on a lectern founds the camp. */
public final class OpeningGameTest {
	/** Works well clear of the other tests: the ruin and the tent are bigger than a test box. */
	private static final int FAR = 64;

	@GameTest
	public void ruinHoldsTheCharter(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Structure ruin = StructureFiles.load(level.getServer().getResourceManager(), HollowRuin.STRUCTURE).orElseThrow();
		BlockPos origin = helper.absolutePos(new BlockPos(0, 1, FAR));
		HollowRuin.build(level, origin, ruin);

		BlockPos chestPos = null;
		for (Structure.Block b : ruin.blocks()) {
			if (b.state().startsWith("minecraft:chest")) chestPos = origin.offset(b.dx(), b.dy(), b.dz());
		}
		helper.assertTrue(chestPos != null, "the ruin has a chest");
		ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(chestPos);
		helper.assertTrue(chest != null, "the chest has a block entity");
		boolean found = false;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (chest.getItem(i).is(CharterItem.ITEM)) found = true;
		}
		helper.assertTrue(found, "the chest holds a Charter");
		helper.assertTrue(level.getBlockState(chestPos.above(0)).is(Blocks.CHEST), "the chest stands in the ruin");
		helper.succeed();
	}

	@GameTest
	public void ruinIsBuiltChunkByChunk(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Structure ruin = StructureFiles.load(level.getServer().getResourceManager(), HollowRuin.STRUCTURE).orElseThrow();
		BlockPos base = helper.absolutePos(new BlockPos(0, 1, FAR + 48));
		// Put the ruin's centre on a chunk corner so its walls fall in four chunks.
		BlockPos origin = new BlockPos(base.getX() >> 4 << 4, base.getY(), base.getZ() >> 4 << 4);
		var chunks = HollowRuin.footprintChunks(origin, ruin);
		helper.assertTrue(chunks.size() >= 4, "the ruin spans several chunks, got " + chunks.size());
		for (var cp : chunks) {
			HollowRuin.placePart(level.getChunk(cp.x(), cp.z()), origin, ruin);
		}
		boolean charter = false;
		int walls = 0;
		for (Structure.Block b : ruin.blocks()) {
			BlockPos p = origin.offset(b.dx(), b.dy(), b.dz());
			if (b.state().startsWith("minecraft:chest")) {
				var be = level.getBlockEntity(p);
				helper.assertTrue(be instanceof ChestBlockEntity, "the chest exists in its own chunk");
				ChestBlockEntity chest = (ChestBlockEntity) be;
				for (int i = 0; i < chest.getContainerSize(); i++) if (chest.getItem(i).is(CharterItem.ITEM)) charter = true;
			}
			if (b.state().equals("minecraft:cobblestone") && level.getBlockState(p).is(Blocks.COBBLESTONE)) walls++;
		}
		helper.assertTrue(charter, "the chest holds a Charter");
		helper.assertTrue(walls > 50, "foundation and walls are placed across the chunks, got " + walls);
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void charterOnLecternFoundsTheCamp(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		RoeServer roe = RoeServer.get().orElseThrow();
		SimState s = roe.state();
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		String uuid = player.getUUID().toString();
		BlockPos lectern = helper.absolutePos(new BlockPos(1, 1, FAR));
		level.setBlock(lectern, Blocks.LECTERN.defaultBlockState(), 3);

		ItemStack charter = new ItemStack(CharterItem.ITEM);
		player.setItemInHand(InteractionHand.MAIN_HAND, charter);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(lectern), Direction.UP, lectern, false);
		UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, hit);

		long id = s.settlementFoundedBy(uuid).orElseThrow(() -> helper.assertionException("no settlement founded"));
		Settlement camp = s.settlement(id).orElseThrow();
		helper.assertTrue(camp.claimRadius() == Balance.STARTING_CLAIM_RADIUS_BLOCKS, "claim radius is the starting radius");
		helper.assertTrue(camp.name().equals("test-mock-player's Camp"), "camp name: " + camp.name());
		Event founded = s.ledger().get(camp.foundedEventId()).orElseThrow();
		helper.assertTrue(founded.type().equals(EventTypes.SETTLEMENT_FOUNDED), "a settlement_founded event was written");
		helper.assertTrue(s.ledger().causeChain(founded.id()).stream().anyMatch(e -> e.type().equals(EventTypes.CHARTER_TAKEN)),
				"founding is caused by the Charter being taken");
		helper.assertTrue(charter.isEmpty(), "the Charter was used up");

		// A second founding by the same player is refused and costs nothing.
		ItemStack second = new ItemStack(CharterItem.ITEM);
		player.setItemInHand(InteractionHand.MAIN_HAND, second);
		int events = s.ledger().size();
		UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, hit);
		helper.assertTrue(s.ledger().size() == events, "a second founding wrote events");
		helper.assertTrue(second.getCount() == 1, "a refused founding keeps the Charter");
		helper.assertTrue(s.settlementFoundedBy(uuid).orElseThrow() == id, "still the first camp");

		// It survives a reload.
		SimState back = roe.saveAndReadBack();
		helper.assertTrue(back.settlementFoundedBy(uuid).orElse(-1L) == id, "the founder survives a reload");
		helper.assertTrue(back.settlement(id).isPresent() && back.settlement(id).get().claimRadius() == Balance.STARTING_CLAIM_RADIUS_BLOCKS,
				"the settlement survives a reload");

		// The tent goes up block by block: after a while a campfire stands beside the lectern.
		helper.runAfterDelay(150, () -> {
			boolean fire = false;
			for (BlockPos p : BlockPos.betweenClosed(lectern.offset(-10, -3, -10), lectern.offset(10, 3, 10))) {
				if (level.getBlockState(p).is(Blocks.CAMPFIRE)) fire = true;
			}
			helper.assertTrue(fire, "the campfire was raised near the lectern");
			helper.succeed();
		});
	}
}
