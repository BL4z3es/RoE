package io.github.bl4z3es.riseofempires.gametest;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Raid;
import io.github.bl4z3es.riseofempires.core.model.RaidStage;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import io.github.bl4z3es.riseofempires.core.sim.Founding;
import io.github.bl4z3es.riseofempires.core.sim.Stores;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import io.github.bl4z3es.riseofempires.mc.raid.RaidEntities;
import io.github.bl4z3es.riseofempires.mc.raid.RaiderEntity;
import io.github.bl4z3es.riseofempires.mc.stockpile.StockpileBlocks;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** The raiders' bodies (task M1-20, embodied half): they take food from the chests, and they can be killed. */
public final class RaidGameTest {
	/** A cleared, floored area near the test, a granary seal in the middle and a chest beside it. */
	private static BlockPos scene(GameTestHelper helper, ServerLevel level) {
		BlockPos base = helper.absolutePos(new BlockPos(0, 0, 0));
		for (int x = -8; x <= 8; x++) {
			for (int z = -8; z <= 8; z++) {
				for (int dy = 1; dy <= 6; dy++) level.setBlockAndUpdate(base.offset(x, dy, z), Blocks.AIR.defaultBlockState());
				level.setBlockAndUpdate(base.offset(x, 0, z), Blocks.STONE.defaultBlockState());
			}
		}
		return base.above();
	}

	private static long settlement(SimState s, ServerLevel level, BlockPos seal, long now) {
		String player = UUID.randomUUID().toString();
		Place centre = RoeServer.place(level, seal);
		Founding.found(s, player, "Raid Test", centre, now).orElseThrow();
		long sid = s.settlementFoundedBy(player).orElseThrow();
		Stores.sealGranary(s, sid, centre, player, now);
		return sid;
	}

	/** Puts a raid at stage ARRIVED into the state, as the core would after the raiders' arrival time. */
	private static long arrivedRaid(SimState s, long sid, int size, long now) {
		var st = s.settlement(sid).orElseThrow();
		Place origin = new Place(st.centre().dimension(), st.centre().x() + Balance.RAID_ORIGIN_DISTANCE_BLOCKS, st.centre().y(), st.centre().z());
		long raidId = s.nextId("raid");
		Ref raidRef = Ref.raid(raidId);
		var planned = s.ledger().append(EventDraft.of(EventTypes.RAID_PLANNED, now).at(origin)
				.actor(raidRef).actor(Ref.settlement(sid)).effect(raidRef, "size", Integer.toString(size))
				.cause(st.foundedEventId()));
		s.put(new Raid(raidId, sid, size, planned.id(), origin, now - 10, now - 1, RaidStage.ARRIVED, size), planned);
		s.putFact("raid." + sid, Long.toString(raidId), planned);
		return raidId;
	}

	private static boolean hasEvent(SimState s, String type, Ref actor) {
		return s.ledger().all().stream().anyMatch(e -> e.type().equals(type) && e.actors().contains(actor));
	}

	private static int count(Container c, net.minecraft.world.item.Item item) {
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
		return n;
	}

	@GameTest(maxTicks = 800, padding = 40)
	public void raidersTakeHalfTheFood(GameTestHelper helper) {
		RoeServer roe = RoeServer.get().orElseThrow();
		SimState s = roe.state();
		ServerLevel level = helper.getLevel();
		BlockPos floor = scene(helper, level);
		BlockPos seal = floor;
		BlockPos chestPos = floor.east(2);
		level.setBlockAndUpdate(seal, StockpileBlocks.GRANARY_SEAL.defaultBlockState());
		level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
		Container chest = (Container) level.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.WHEAT, 10));
		chest.setItem(1, new ItemStack(Items.BREAD, 4));
		long now = roe.now();
		long sid = settlement(s, level, seal, now);
		long raidId = arrivedRaid(s, sid, 1, now);
		RaiderEntity raider = RaiderEntity.spawn(level, raidId, floor.south(2));
		helper.assertTrue(raider != null, "the raider was not created");
		helper.succeedWhen(() -> {
			helper.assertTrue(count(chest, Items.WHEAT) == 5, "the chest holds " + count(chest, Items.WHEAT) + " wheat, not 5; raider "
					+ (raider.isAlive() ? "at " + raider.blockPosition() + " (seal " + seal + ") order " + raider.order() + " at target " + raider.ticksAtTarget() : "dead")
					+ ", raid " + s.raid(raidId).map(r -> r.stage().toString()).orElse("?") + ", embodied " + roe.isEmbodied(RoeServer.place(level, seal))
					+ ", granary " + s.settlement(sid).map(st -> String.valueOf(st.granary())).orElse("?") + ", tod " + roe.now() % 24_000);
			helper.assertTrue(count(chest, Items.BREAD) == 2, "the chest holds " + count(chest, Items.BREAD) + " bread, not 2");
			helper.assertTrue(hasEvent(s, EventTypes.GOODS_STOLEN, Ref.raid(raidId)), "no goods_stolen event");
			helper.assertTrue(s.raid(raidId).orElseThrow().stage() == RaidStage.ENDED, "the raid has not ended");
		});
	}

	@GameTest(maxTicks = 200, padding = 40)
	public void killedRaiderDrivesTheRaidOff(GameTestHelper helper) {
		RoeServer roe = RoeServer.get().orElseThrow();
		SimState s = roe.state();
		ServerLevel level = helper.getLevel();
		BlockPos floor = scene(helper, level);
		level.setBlockAndUpdate(floor, StockpileBlocks.GRANARY_SEAL.defaultBlockState());
		long now = roe.now();
		long sid = settlement(s, level, floor, now);
		long raidId = arrivedRaid(s, sid, 1, now);
		RaiderEntity raider = RaiderEntity.spawn(level, raidId, floor.south(5));
		helper.assertTrue(raider != null, "the raider was not created");
		raider.kill(level);
		helper.assertTrue(hasEvent(s, EventTypes.RAIDER_KILLED, Ref.raid(raidId)), "no raider_killed event");
		helper.assertTrue(hasEvent(s, EventTypes.RAID_ENDED, Ref.raid(raidId)), "no raid_ended event");
		helper.assertTrue(s.raid(raidId).orElseThrow().stage() == RaidStage.ENDED, "the raid has not ended");
		helper.assertTrue(s.raid(raidId).orElseThrow().raidersLeft() == 0, "a raider is still counted");
		helper.succeed();
	}

	@GameTest(maxTicks = 20, padding = 40)
	public void raiderIsWeakAndArmed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos floor = scene(helper, level);
		RaiderEntity r = RaiderEntity.spawn(level, 0, floor.south(5));
		helper.assertTrue(r != null, "the raider was not created");
		helper.assertTrue(r.getMaxHealth() == Balance.RAIDER_HEALTH && r.getHealth() == Balance.RAIDER_HEALTH,
				"health is " + r.getHealth() + "/" + r.getMaxHealth());
		helper.assertTrue(r.getMainHandItem().is(Items.DIAMOND_AXE), "no diamond axe");
		helper.assertTrue(r.getItemBySlot(EquipmentSlot.HEAD).is(Items.LEATHER_HELMET), "no leather helmet");
		helper.assertTrue(r.getItemBySlot(EquipmentSlot.CHEST).is(Items.LEATHER_CHESTPLATE), "no leather chestplate");
		helper.assertTrue(r.getItemBySlot(EquipmentSlot.LEGS).is(Items.LEATHER_LEGGINGS), "no leather leggings");
		helper.assertTrue(r.getItemBySlot(EquipmentSlot.FEET).is(Items.LEATHER_BOOTS), "no leather boots");
		helper.assertTrue(r.getType() == RaidEntities.RAIDER, "wrong type");
		r.discard();
		helper.succeed();
	}
}
