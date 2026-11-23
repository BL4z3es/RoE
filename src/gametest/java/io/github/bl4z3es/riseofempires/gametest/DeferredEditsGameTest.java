package io.github.bl4z3es.riseofempires.gametest;

import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.model.Blueprint;
import io.github.bl4z3es.riseofempires.core.model.BlueprintBlock;
import io.github.bl4z3es.riseofempires.core.model.BuildSite;
import io.github.bl4z3es.riseofempires.core.model.Profession;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import io.github.bl4z3es.riseofempires.core.sim.Building;
import io.github.bl4z3es.riseofempires.core.sim.Founding;
import io.github.bl4z3es.riseofempires.core.sim.Jobs;
import io.github.bl4z3es.riseofempires.core.sim.LedgerTier;
import io.github.bl4z3es.riseofempires.core.sim.Stores;
import io.github.bl4z3es.riseofempires.core.sim.Survivors;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import io.github.bl4z3es.riseofempires.mc.world.DeferredEdits;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * Task M1-17: a builder finishes a blueprint while nobody is there to watch, and the building is
 * standing when the area is next seen. The settlement's centre is far outside the loaded world, so it
 * runs in the Ledger tier; its build site is in the test area, where the queued edits are applied.
 */
public final class DeferredEditsGameTest {
	@GameTest
	public void offCameraBuildingStandsOnReturn(GameTestHelper helper) {
		RoeServer roe = RoeServer.get().orElseThrow();
		SimState s = roe.state();
		long now = roe.now();
		String player = UUID.randomUUID().toString();
		Place far = new Place("minecraft:overworld", 2_000_000, 64, 2_000_000);
		Founding.found(s, player, "Farhold", far, now).orElseThrow();
		long sid = s.settlementFoundedBy(player).orElseThrow();
		helper.assertFalse(roe.isEmbodied(far), "the test settlement must be off-camera");
		Stores.sealGranary(s, sid, far, player, now);
		Stores.recount(s, sid, Map.of("minecraft:cobblestone", 20), false, now);
		Survivors.setOut(s, sid, now, List.of(
				new Survivors.Template("Ada", "steady", "a roof", "a fire"),
				new Survivors.Template("Bram", "quick", "his brother", "a road"),
				new Survivors.Template("Cora", "proud", "land", "an empire")));
		for (long id : Survivors.pending(s, sid)) Survivors.join(s, id, sid, now, far);
		Jobs.assign(s, s.residents(sid).get(0).id(), Profession.BUILDER, player, now);

		Blueprint wall = new Blueprint("riseofempires:test_wall", "Test wall", List.of(
				new BlueprintBlock(0, 0, 0, "minecraft:cobblestone", "minecraft:cobblestone"),
				new BlueprintBlock(1, 0, 0, "minecraft:cobblestone", "minecraft:cobblestone"),
				new BlueprintBlock(2, 0, 0, "minecraft:cobblestone", "minecraft:cobblestone"),
				new BlueprintBlock(3, 0, 0, "minecraft:cobblestone", "minecraft:cobblestone")));
		Map<String, Blueprint> blueprints = Map.of(wall.id(), wall);
		BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
		Building.placeBlueprint(s, sid, wall, RoeServer.place(helper.getLevel(), origin), 0, player, now);
		long siteId = Building.activeSite(s, sid).orElseThrow().id();

		// Two in-game hours pass with nobody watching.
		LedgerTier.advance(s, sid, now, blueprints);
		LedgerTier.Result r = LedgerTier.advance(s, sid, now + 2_000 + 999, blueprints);
		helper.assertTrue(r.built().size() == 4, "expected 4 blocks built off-camera, got " + r.built().size());
		DeferredEdits.queueBuilt(roe, r.built(), blueprints);
		Building.completeIfDone(s, siteId, r.events().get(r.events().size() - 1), now, blueprints).orElseThrow();
		BuildSite site = s.buildSite(siteId).orElseThrow();
		helper.assertTrue(site.complete(), "site should be complete");
		helper.assertTrue(s.stockpile(sid).total("minecraft:cobblestone") == 16, "four cobblestone should be used");
		helper.assertTrue(s.ledger().latestOfType(EventTypes.BLUEPRINT_COMPLETED).isPresent(), "completion not in the Ledger");

		// The area loads with no player near: the queued edits are applied.
		DeferredEdits.INSTANCE.tick(roe, now);
		for (int x = 0; x < 4; x++) helper.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(1 + x, 2, 1));
		helper.succeed();
	}
}
