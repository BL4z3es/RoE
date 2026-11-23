package io.github.bl4z3es.riseofempires.gametest;

import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import io.github.bl4z3es.riseofempires.core.sim.Founding;
import io.github.bl4z3es.riseofempires.core.sim.Survivors;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import io.github.bl4z3es.riseofempires.mc.entity.CharacterEntity;
import io.github.bl4z3es.riseofempires.mc.stockpile.StockpileBlocks;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * A flat stone floor cleared of the test structure's walls, with a founded settlement, a Granary Seal and
 * one chest, for the job tests. The empty test structure has walls that block paths, so the tests use
 * {@code padding = 40} and this clears the area (see CharacterGameTest).
 */
final class JobScene {
	final RoeServer roe = RoeServer.get().orElseThrow();
	final SimState state = roe.state();
	final ServerLevel level;
	/** Chunk-aligned corner of the cleared area, at floor height. */
	final BlockPos origin;
	final String player = UUID.randomUUID().toString();
	final long sid;
	final BlockPos chestPos;
	final List<Long> people;

	/** Clears (2r+1) by (2r+1) chunks around the test, forcing them to stay loaded when r is above 0. */
	JobScene(GameTestHelper helper, int chunkRadius) {
		level = helper.getLevel();
		helper.setTime(Math.floorDiv(roe.now(), 24_000L) * 24_000L + 1_000);
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		int x0 = Math.floorDiv(base.getX(), 16) * 16, z0 = Math.floorDiv(base.getZ(), 16) * 16;
		origin = new BlockPos(x0, base.getY(), z0);
		for (int cx = -chunkRadius; cx <= chunkRadius; cx++) {
			for (int cz = -chunkRadius; cz <= chunkRadius; cz++) {
				if (chunkRadius > 0) level.setChunkForced((x0 >> 4) + cx, (z0 >> 4) + cz, true);
			}
		}
		for (int x = x0 - 16 * chunkRadius; x < x0 + 16 + 16 * chunkRadius; x++) {
			for (int z = z0 - 16 * chunkRadius; z < z0 + 16 + 16 * chunkRadius; z++) {
				for (int dy = 1; dy <= 10; dy++) level.setBlock(new BlockPos(x, base.getY() + dy, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				level.setBlock(new BlockPos(x, base.getY(), z), Blocks.POLISHED_ANDESITE.defaultBlockState(), Block.UPDATE_CLIENTS);
				level.setBlock(new BlockPos(x, base.getY() - 1, z), Blocks.POLISHED_ANDESITE.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		long now = roe.now();
		BlockPos centre = at(1, 1, 1);
		Founding.found(state, player, "Jobford", RoeServer.place(level, centre), now).orElseThrow();
		sid = state.settlementFoundedBy(player).orElseThrow();
		BlockPos seal = at(1, 1, 3);
		chestPos = at(3, 1, 3);
		level.setBlock(seal, StockpileBlocks.GRANARY_SEAL.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		io.github.bl4z3es.riseofempires.core.sim.Stores.sealGranary(state, sid, RoeServer.place(level, seal), player, now);
		Survivors.setOut(state, sid, now, List.of(
				new Survivors.Template("Ada", "steady", "a roof", "the fire at Elmford"),
				new Survivors.Template("Bram", "quick", "his brother", "the long road"),
				new Survivors.Template("Cora", "proud", "land", "the old empire")));
		people = Survivors.pending(state, sid);
		for (long id : people) Survivors.join(state, id, sid, now, RoeServer.place(level, centre));
	}

	/** A position relative to the cleared area's corner; y 0 is the stone floor. */
	BlockPos at(int dx, int dy, int dz) { return origin.offset(dx, dy, dz); }

	Place place(BlockPos p) { return RoeServer.place(level, p); }

	Container chest() { return (Container) level.getBlockEntity(chestPos); }

	CharacterEntity spawn(long id, BlockPos pos) {
		CharacterEntity body = CharacterEntity.spawn(level, id, pos);
		if (body == null) throw new IllegalStateException("the body was not created");
		return body;
	}

	int inChest(Item item) {
		Container c = chest();
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
		return n;
	}

	/** Puts the amount in the chest, in stacks of the most the item allows. */
	void stock(Item item, int amount) {
		Container c = chest();
		int max = new ItemStack(item).getMaxStackSize();
		for (int slot = 0; slot < c.getContainerSize() && amount > 0; slot++) {
			if (!c.getItem(slot).isEmpty()) continue;
			int n = Math.min(max, amount);
			c.setItem(slot, new ItemStack(item, n));
			amount -= n;
		}
		if (amount > 0) throw new IllegalStateException("the chest is full");
	}

	/** True if the Ledger holds an event of this type with the character among its actors. */
	boolean hasEvent(String type, long characterId) {
		for (Event e : state.ledger().all()) {
			if (e.type().equals(type) && e.actors().stream().anyMatch(a -> a.kind().equals(Ref.CHARACTER) && a.id().equals(Long.toString(characterId)))) return true;
		}
		return false;
	}

	boolean hasEvent(String type) {
		for (Event e : state.ledger().all()) if (e.type().equals(type)) return true;
		return false;
	}
}
