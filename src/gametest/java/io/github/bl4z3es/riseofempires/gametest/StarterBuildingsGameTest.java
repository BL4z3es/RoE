package io.github.bl4z3es.riseofempires.gametest;

import io.github.bl4z3es.riseofempires.core.model.Blueprint;
import io.github.bl4z3es.riseofempires.core.model.BlueprintBlock;
import io.github.bl4z3es.riseofempires.core.model.BuildSite;
import io.github.bl4z3es.riseofempires.core.model.Profession;
import io.github.bl4z3es.riseofempires.core.sim.Building;
import io.github.bl4z3es.riseofempires.core.sim.Jobs;
import io.github.bl4z3es.riseofempires.mc.entity.CharacterEntity;
import io.github.bl4z3es.riseofempires.mc.job.JobModule;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

/**
 * Task M1-13: builders build the real farm, stable and bunkhouse start to finish from a chest holding
 * everything they need. Slow, so manual only: run it from the in-game gametest command.
 */
public final class StarterBuildingsGameTest {
	@GameTest(maxTicks = 30_000, padding = 40, manualOnly = true)
	public void buildersBuildTheFarm(GameTestHelper helper) { build(helper, Blueprint.FARM); }

	@GameTest(maxTicks = 30_000, padding = 40, manualOnly = true)
	public void buildersBuildTheStable(GameTestHelper helper) { build(helper, Blueprint.STABLE); }

	@GameTest(maxTicks = 30_000, padding = 40, manualOnly = true)
	public void buildersBuildTheBunkhouse(GameTestHelper helper) { build(helper, Blueprint.BUNKHOUSE); }

	private static void build(GameTestHelper helper, String blueprintId) {
		JobScene w = new JobScene(helper, 1);
		long now = w.roe.now();
		Blueprint bp = w.roe.content().blueprints().get(blueprintId);
		Map<String, Integer> need = new TreeMap<>();
		for (BlueprintBlock b : bp.blocks()) {
			if (!b.item().isEmpty() && !b.blockState().equals("minecraft:air")) need.merge(b.item(), 1, Integer::sum);
		}
		// A long build crosses meals: without food the builders would fall ill and stop.
		w.stock(net.minecraft.world.item.Items.BREAD, 192);
		need.forEach((item, n) -> w.stock(BuiltInRegistries.ITEM.getValue(Identifier.parse(item)), n));
		BlockPos origin = w.at(20, 0, 20);
		Building.placeBlueprint(w.state, w.sid, bp, w.place(origin), 0, w.player, now);
		BuildSite site = Building.activeSite(w.state, w.sid).orElseThrow();
		long started = helper.getTick();
		java.util.List<CharacterEntity> bodies = new java.util.ArrayList<>();
		for (int i = 0; i < 2; i++) {
			long id = w.people.get(i);
			Jobs.assign(w.state, id, Profession.BUILDER, w.player, now);
			CharacterEntity b = w.spawn(id, w.at(6 + i, 1, 6));
			bodies.add(b);
			JobModule.sync(w.roe, b);
		}
		helper.succeedWhen(() -> {
			BuildSite s = w.state.buildSite(site.id()).orElseThrow();
			String next = s.complete() ? "" : bp.blocks().get(s.placed()).toString() + " at " + Building.worldPos(s, bp.blocks().get(s.placed()));
			helper.assertTrue(s.complete(), s.placed() + " of " + s.total() + " blocks after " + (helper.getTick() - started) + " ticks; next " + next
					+ "; bodies " + bodies.stream().map(b -> b.position() + " " + b.carried() + " " + b.record().map(c -> c.condition().toString()).orElse("?") + " tod=" + w.roe.now() % 24000).toList());
			io.github.bl4z3es.riseofempires.RiseOfEmpires.LOGGER.info("[StarterBuildingsGameTest] {} built in {} ticks", blueprintId, helper.getTick() - started);
		});
	}
}
