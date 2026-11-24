package io.github.bl4z3es.riseofempires.gametest;

import io.github.bl4z3es.riseofempires.RiseOfEmpires;
import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import io.github.bl4z3es.riseofempires.core.sim.Founding;
import io.github.bl4z3es.riseofempires.core.sim.Survivors;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import io.github.bl4z3es.riseofempires.mc.entity.CharacterEntity;
import io.github.bl4z3es.riseofempires.mc.entity.RoeEntities;
import io.github.bl4z3es.riseofempires.mc.world.WorldBlocks;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * Tasks M1-07, M1-11 and the embodied half of M1-15, in one test because the last part moves the
 * world clock, which every body in the world reads.
 *
 * <ol>
 * <li>A body is made for a character with no player near, walks 13 blocks and puts an item in a chest.
 * <li>Its saved data loads into a new body with the same identity and cargo.
 * <li>At night an idle body walks to a free bed under a roof and sleeps; at dawn it wakes.
 * <li>At night a survivor still walking to a camp (they set out at dusk) keeps walking: night is only for
 *     people who belong to a camp.
 * </ol>
 */
public final class CharacterGameTest {
	private static final int WALK_BLOCKS = 14;

	private static String floorRow(ServerLevel level, int x0, int y, int z) {
		StringBuilder sb = new StringBuilder();
		for (int dy = -1; dy <= 3; dy++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int x = x0; x < x0 + 16; x++) {
					var st = level.getBlockState(new BlockPos(x, y + dy, z + dz));
					sb.append(st.isAir() ? '.' : st.is(Blocks.STONE) ? 'S' : st.is(Blocks.CHEST) ? 'C' : '?');
				}
				sb.append('|');
			}
			sb.append("//");
		}
		return sb.toString();
	}

	// Padding keeps the neighbouring test structures (walls of other tests) away from this scene.
	@GameTest(maxTicks = 1500, padding = 40)
	public void bodyWalksCarriesReloadsAndSleeps(GameTestHelper helper) {
		RoeServer roe = RoeServer.get().orElseThrow();
		SimState s = roe.state();
		ServerLevel level = helper.getLevel();
		// The world clock carries over between runs and bodies keep night hours, so start in the morning.
		helper.setTime(Math.floorDiv(roe.now(), 24_000L) * 24_000L + 1_000);
		long now = roe.now();
		long dayStart = now - 1_000;

		// Keep the whole scene inside one chunk, so every block on the paths is loaded.
		BlockPos base = helper.absolutePos(new BlockPos(0, 0, 0));
		int x0 = Math.floorDiv(base.getX(), 16) * 16;
		int z0 = Math.floorDiv(base.getZ(), 16) * 16 + 8;
		for (int x = x0 - 1; x <= x0 + 16; x++) {
			for (int z = z0 - 4; z <= z0 + 4; z++) {
				// Clear whatever the test structure left here (it has walls), then lay the floor.
				for (int dy = 1; dy <= 6; dy++) level.setBlockAndUpdate(new BlockPos(x, base.getY() + dy, z), Blocks.AIR.defaultBlockState());
				level.setBlockAndUpdate(new BlockPos(x, base.getY(), z), Blocks.STONE.defaultBlockState());
			}
		}
		// Walls keep the other bodies in the world (the persistence test's residents wander nearby) out of the room.
		for (int y = 1; y <= 3; y++) {
			for (int x = x0 - 1; x <= x0 + 16; x++) {
				level.setBlockAndUpdate(new BlockPos(x, base.getY() + y, z0 - 4), Blocks.STONE.defaultBlockState());
				level.setBlockAndUpdate(new BlockPos(x, base.getY() + y, z0 + 4), Blocks.STONE.defaultBlockState());
			}
			for (int z = z0 - 4; z <= z0 + 4; z++) {
				level.setBlockAndUpdate(new BlockPos(x0 - 1, base.getY() + y, z), Blocks.STONE.defaultBlockState());
				level.setBlockAndUpdate(new BlockPos(x0 + 16, base.getY() + y, z), Blocks.STONE.defaultBlockState());
			}
		}
		BlockPos start = new BlockPos(x0 + 1, base.getY() + 1, z0);
		BlockPos chestPos = start.east(WALK_BLOCKS - 1);
		level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());

		String player = UUID.randomUUID().toString();
		Place centre = RoeServer.place(level, start);
		Founding.found(s, player, "Walkhold", centre, now).orElseThrow();
		long sid = s.settlementFoundedBy(player).orElseThrow();
		Survivors.setOut(s, sid, now, List.of(
				new Survivors.Template("Ada", "steady", "a roof", "the fire at Elmford"),
				new Survivors.Template("Bram", "quick", "his brother", "the long road"),
				new Survivors.Template("Cora", "proud", "land", "the old empire")));
		long id = Survivors.pending(s, sid).get(0);
		// All three join, so the module does not later send them walking in through the test room.
		for (long survivor : Survivors.pending(s, sid)) Survivors.join(s, survivor, sid, now, centre);

		helper.assertTrue(!WorldBlocks.playerNear(level, start), "a player is within 48 blocks of the test");
		CharacterEntity first = CharacterEntity.spawn(level, id, start);
		helper.assertTrue(first != null, "the body was not created");
		helper.assertTrue(first.characterId() == id, "wrong character id");
		helper.assertTrue(first.record().isPresent() && first.getCustomName() != null
				&& first.getCustomName().getString().equals(first.record().get().name()), "body does not show the record's name");
		helper.assertTrue(first.carry(new ItemStack(Items.WHEAT, 5)).isEmpty(), "carry refused");
		helper.assertTrue(first.carriedCount() == 5 && first.carried().equals(Map.of("minecraft:wheat", 5)), "wrong carried items");
		helper.assertTrue(first.getMainHandItem().is(Items.WHEAT), "the carried stack is not in the hand");

		long startedAt = level.getGameTime();
		double startX = first.getX();
		long[] walkTicks = {-1};
		double[] walked = {0};
		first.setJob(b -> {
			if (walkTicks[0] >= 0) return;
			if (level.getGameTime() % 10 == 0 && (b.getY() < start.getY() - 0.5 || b.tickCount < 200 && b.tickCount % 30 == 0)) {
				RiseOfEmpires.LOGGER.info("[CharacterGameTest] t={} pos={} nav={} path={}", b.tickCount, b.position(), b.getNavigation().isDone(), b.getNavigation().getPath());
			}
			if (b.walkTo(chestPos, 1.5)) {
				walkTicks[0] = level.getGameTime() - startedAt;
				walked[0] = Math.abs(b.getX() - startX);
				b.depositAll((Container) level.getBlockEntity(chestPos));
				b.setJob(null);
			}
		});

		// Stage 0: wait for the deposit. Stage 1: reload. Stage 2: night and bed. Stage 3: dawn.
		int[] stage = {0};
		CharacterEntity[] body = {first};
		CharacterEntity[] traveller = {null};
		// Walks west, away from the beds: a body that took the night off would go east to a bed or stand still.
		BlockPos travelFrom = new BlockPos(x0 + 8, base.getY() + 1, z0 + 3);
		BlockPos travelTo = travelFrom.west(7);
		BlockPos foot = new BlockPos(x0 + 10, base.getY() + 1, z0);
		helper.succeedWhen(() -> {
			if (stage[0] == 0) {
				Container chest = (Container) level.getBlockEntity(chestPos);
				int wheat = 0;
				for (int i = 0; i < chest.getContainerSize(); i++) if (chest.getItem(i).is(Items.WHEAT)) wheat += chest.getItem(i).getCount();
				helper.assertTrue(wheat == 5, "the chest holds " + wheat + " wheat, not 5; body at " + first.position() + " chest " + chestPos
						+ " navDone=" + first.getNavigation().isDone() + " tod=" + roe.now() % 24_000 + " floor=" + floorRow(level, x0, base.getY(), z0)
						+ " others=" + level.getEntitiesOfClass(CharacterEntity.class, first.getBoundingBox().inflate(40)).size());
				helper.assertTrue(first.carriedCount() == 0, "the body still carries items");
				double ticksPerBlock = walkTicks[0] / Math.max(1.0, walked[0]);
				RiseOfEmpires.LOGGER.info("[CharacterGameTest] walked {} blocks in {} ticks: {} ticks per block (target {})",
						walked[0], walkTicks[0], ticksPerBlock, Balance.WALK_TICKS_PER_BLOCK);
				helper.assertTrue(walked[0] >= 8, "walked only " + walked[0] + " blocks");
				helper.assertTrue(Math.abs(ticksPerBlock - Balance.WALK_TICKS_PER_BLOCK) <= 1.5,
						"walking takes " + ticksPerBlock + " ticks per block, not about " + Balance.WALK_TICKS_PER_BLOCK);

				// Carry something again, save the body, and load a new one from the saved data.
				first.carry(new ItemStack(Items.BREAD, 3));
				TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
				first.saveWithoutId(out);
				CompoundTag tag = out.buildResult();
				first.discard();
				CharacterEntity again = RoeEntities.CHARACTER.create(level, EntitySpawnReason.LOAD);
				again.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
				level.addFreshEntity(again);
				helper.assertTrue(again.characterId() == id, "reloaded body has character id " + again.characterId());
				helper.assertTrue(again.carried().equals(Map.of("minecraft:bread", 3)), "reloaded body carries " + again.carried());
				helper.assertTrue(again.getMainHandItem().is(Items.BREAD), "reloaded body does not hold what it carries");
				helper.assertTrue(again.record().isPresent(), "the record is gone");
				body[0] = again;

				// Set the night scene: three beds under a roof (the other two residents get bodies too), and dusk.
				for (int x = x0 + 8; x <= x0 + 12; x++) {
					for (int z = z0 - 3; z <= z0 + 3; z++) {
						level.setBlockAndUpdate(new BlockPos(x, base.getY() + 4, z), Blocks.STONE.defaultBlockState());
					}
				}
				BlockState bed = Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
				for (int dz = -2; dz <= 2; dz += 2) {
					level.setBlockAndUpdate(foot.south(dz), bed.setValue(BedBlock.PART, BedPart.FOOT));
					level.setBlockAndUpdate(foot.south(dz).east(), bed.setValue(BedBlock.PART, BedPart.HEAD));
				}
				helper.setTime(dayStart + 13_000);
				// Someone on the road who belongs to no camp yet, as survivors are until they join. A bare
				// record, not a camp's pending survivor, so no other test's clock can make them arrive.
				var met = s.ledger().append(io.github.bl4z3es.riseofempires.core.ledger.EventDraft.of("test_traveller", roe.now()));
				var wanderer = new io.github.bl4z3es.riseofempires.core.model.Character(s.nextId("character"), "Dag", "patient", "a bed",
						io.github.bl4z3es.riseofempires.core.model.Profession.NONE, List.of(),
						io.github.bl4z3es.riseofempires.core.model.Needs.MET, io.github.bl4z3es.riseofempires.core.model.Condition.WELL, 0);
				s.put(wanderer, met);
				traveller[0] = CharacterEntity.spawn(level, wanderer.id(), travelFrom);
				helper.assertTrue(traveller[0] != null, "the traveller's body was not created");
				traveller[0].setJob(t -> t.walkTo(travelTo, 1.0));
				stage[0] = 2;
				helper.assertTrue(false, "night scene set");
			}
			CharacterEntity b = body[0];
			if (stage[0] == 2) {
				helper.assertTrue(b.isSleeping(), "the body is not asleep at night; body at " + b.position() + " foot " + foot
						+ " navDone=" + b.getNavigation().isDone() + " tod=" + roe.now() % 24_000
						+ " bodies=" + level.getEntitiesOfClass(CharacterEntity.class, b.getBoundingBox().inflate(60)).stream()
								.map(e -> e.characterId() + "@" + e.blockPosition() + (e.isSleeping() ? "z" : "")).toList());
				helper.assertTrue(b.getSleepingPos().map(p -> p.getX() >= foot.getX() && p.getX() <= foot.getX() + 1
						&& Math.abs(p.getZ() - foot.getZ()) <= 2 && p.getY() == foot.getY()).orElse(false), "the body sleeps somewhere else");
				helper.assertTrue(traveller[0].getX() <= travelFrom.getX() - 4, "a survivor still on the road stopped walking at night: at "
						+ traveller[0].position() + " tod=" + roe.now() % 24_000);
				traveller[0].discard();
				helper.setTime(dayStart + 24_000 + 1_000);
				stage[0] = 3;
				helper.assertTrue(false, "dawn set");
			}
			helper.assertTrue(!b.isSleeping(), "the body is still asleep in the morning");
			b.discard();
		});
	}
}
