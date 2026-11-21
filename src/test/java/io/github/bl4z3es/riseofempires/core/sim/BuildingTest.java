package io.github.bl4z3es.riseofempires.core.sim;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.model.Blueprint;
import io.github.bl4z3es.riseofempires.core.model.BlueprintBlock;
import io.github.bl4z3es.riseofempires.core.model.BuildSite;
import io.github.bl4z3es.riseofempires.core.model.SimState;

class BuildingTest {
	private static final Place CENTRE = new Place("overworld", 0, 64, 0);

	private static final Blueprint FARM = new Blueprint(Blueprint.FARM, "Farm", List.of(
			new BlueprintBlock(0, 0, 0, "minecraft:farmland", ""),
			new BlueprintBlock(1, 1, 0, "minecraft:wheat[age=0]", "minecraft:wheat_seeds"),
			new BlueprintBlock(2, 1, 0, "minecraft:wheat[age=0]", "minecraft:wheat_seeds")));
	private static final Blueprint BUNK = new Blueprint(Blueprint.BUNKHOUSE, "Bunkhouse", List.of(
			new BlueprintBlock(0, 0, 0, "minecraft:red_bed[facing=north,part=head]", "minecraft:red_bed"),
			new BlueprintBlock(0, 0, 1, "minecraft:red_bed[facing=north,part=foot]", ""),
			new BlueprintBlock(3, 0, 0, "minecraft:red_bed[facing=north,part=head]", "minecraft:red_bed")));
	private static final Map<String, Blueprint> BPS = Map.of(FARM.id(), FARM, BUNK.id(), BUNK);

	private static SimState camp() {
		SimState s = new SimState(5);
		Founding.found(s, "p1", "Camp", CENTRE, 0).orElseThrow();
		return s;
	}

	@Test
	void rotation() {
		assertArrayEquals(new int[] {2, 5}, Building.rotate(2, 5, 0));
		assertArrayEquals(new int[] {-5, 2}, Building.rotate(2, 5, 1));
		assertArrayEquals(new int[] {-2, -5}, Building.rotate(2, 5, 2));
		assertArrayEquals(new int[] {5, -2}, Building.rotate(2, 5, 3));
		assertArrayEquals(new int[] {2, 5}, Building.rotate(2, 5, 4));
	}

	@Test
	void worldPosUsesRotationAndOrigin() {
		Place origin = new Place("overworld", 10, 64, 20);
		BuildSite site = new BuildSite(1, 1, "x", origin, 1, 0, 1, 1);
		assertEquals(new Place("overworld", 10, 66, 22), Building.worldPos(site, new BlueprintBlock(2, 2, 0, "s", "")));
	}

	@Test
	void completionRegistersWheatPlots() {
		SimState s = camp();
		Event placed = Building.placeBlueprint(s, 1, FARM, new Place("overworld", 10, 64, 10), 0, "p1", 10);
		assertEquals(EventTypes.BLUEPRINT_PLACED, placed.type());
		assertEquals(FARM.blocks().size(), Building.activeSite(s, 1).orElseThrow().total());
		List<Event> first = Building.blocksBuilt(s, 1, 1, 7, 20, BPS);
		assertEquals(1, first.size());
		assertEquals(List.of(placed.id()), first.get(0).causes());
		assertTrue(s.farm(1).isEmpty());
		List<Event> rest = Building.blocksBuilt(s, 1, 2, 7, 30, BPS);
		assertEquals(2, rest.size());
		assertEquals(EventTypes.BLUEPRINT_COMPLETED, rest.get(1).type());
		assertEquals(List.of(rest.get(0).id()), rest.get(1).causes());
		assertEquals(2, s.farm(1).size());
		assertEquals(new Place("overworld", 11, 65, 10), s.farm(1).get(0).pos());
		assertTrue(Building.activeSite(s, 1).isEmpty());
	}

	@Test
	void completionCountsBeds() {
		SimState s = camp();
		Building.placeBlueprint(s, 1, BUNK, new Place("overworld", 10, 64, 10), 0, "p1", 10);
		Building.blocksBuilt(s, 1, 3, 0, 20, BPS);
		assertEquals("2", s.fact("beds.1").orElseThrow());
	}

	@Test
	void placedNeverExceedsTotal() {
		SimState s = camp();
		Building.placeBlueprint(s, 1, FARM, new Place("overworld", 10, 64, 10), 0, "p1", 10);
		Building.blocksBuilt(s, 1, 100, 7, 20, BPS);
		BuildSite site = s.buildSite(1).orElseThrow();
		assertEquals(site.total(), site.placed());
		assertTrue(Building.blocksBuilt(s, 1, 5, 7, 30, BPS).isEmpty());
		assertEquals(site.total(), s.buildSite(1).orElseThrow().placed());
	}
}
