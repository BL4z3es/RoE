package io.github.bl4z3es.riseofempires.core.sim;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.model.Plot;
import io.github.bl4z3es.riseofempires.core.model.SimState;

class StoresTest {
	private static final Place CENTRE = new Place("overworld", 0, 64, 0);
	private static final String WHEAT = "minecraft:wheat";

	private static SimState camp() {
		SimState s = new SimState(5);
		Founding.found(s, "p1", "Camp", CENTRE, 0).orElseThrow();
		return s;
	}

	@Test
	void sealGranarySetsPlaceWithFoundedCause() {
		SimState s = camp();
		Place at = new Place("overworld", 3, 64, 3);
		Event e = Stores.sealGranary(s, 1, at, "p1", 10);
		assertEquals(EventTypes.GRANARY_SEALED, e.type());
		assertEquals(java.util.List.of(s.settlement(1).orElseThrow().foundedEventId()), e.causes());
		assertEquals(at, s.settlement(1).orElseThrow().granary());
		assertEquals(e.id(), s.lastStockEvent(1));
	}

	@Test
	void recountOnlyWritesWhenDifferent() {
		SimState s = camp();
		long before = s.ledger().size();
		assertTrue(Stores.recount(s, 1, Map.of(), false, 10).isEmpty());
		assertEquals(before, s.ledger().size());
		Event e = Stores.recount(s, 1, Map.of(WHEAT, 5), false, 20).orElseThrow();
		assertEquals(EventTypes.STOCKPILE_COUNTED, e.type());
		assertEquals("+5", e.effects().get(0).detail());
		assertEquals(5, s.stockpile(1).total(WHEAT));
		assertTrue(Stores.recount(s, 1, Map.of(WHEAT, 5), false, 30).isEmpty());
		Event down = Stores.recount(s, 1, Map.of(WHEAT, 2), false, 40).orElseThrow();
		assertEquals("-3", down.effects().get(0).detail());
		assertEquals(java.util.List.of(e.id()), down.causes());
	}

	@Test
	void flushedRecountClearsPending() {
		SimState s = camp();
		Event seed = Stores.recount(s, 1, Map.of(WHEAT, 4), false, 10).orElseThrow();
		s.put(s.stockpile(1).withPendingChange(WHEAT, 3), seed);
		assertEquals(7, s.stockpile(1).total(WHEAT));
		Event e = Stores.recount(s, 1, Map.of(WHEAT, 4), true, 20).orElseThrow();
		assertTrue(s.stockpile(1).pending().isEmpty());
		assertEquals(4, s.stockpile(1).total(WHEAT));
		assertTrue(e.effects().isEmpty());
	}

	@Test
	void depositAndTake() {
		SimState s = camp();
		Stores.deposit(s, 1, 9, Map.of(WHEAT, 6), 10);
		assertEquals(6, s.stockpile(1).total(WHEAT));
		Stores.take(s, 1, 9, Map.of(WHEAT, 4), 20);
		assertEquals(2, s.stockpile(1).total(WHEAT));
		Event e = Stores.take(s, 1, 9, Map.of(WHEAT, 10), 30);
		assertEquals(Stores.GOODS_TAKEN, e.type());
		assertEquals(0, s.stockpile(1).total(WHEAT));
		assertEquals(e.id(), s.lastStockEvent(1));
	}

	@Test
	void harvestResetsPlotAndLeavesStockpile() {
		SimState s = camp();
		Place plot = new Place("overworld", 5, 63, 5);
		Event seed = Stores.deposit(s, 1, 9, Map.of(WHEAT, 1), 1);
		s.putFarm(1, java.util.List.of(new Plot(plot, WHEAT, 7), new Plot(new Place("overworld", 6, 63, 5), WHEAT, 7)), seed);
		Event e = Stores.harvested(s, 1, 9, plot, Map.of(WHEAT, 1), 50);
		assertEquals(EventTypes.CROP_HARVESTED, e.type());
		assertEquals(0, s.farm(1).get(0).age());
		assertEquals(7, s.farm(1).get(1).age());
		assertEquals(1, s.stockpile(1).total(WHEAT));
		Event g = Stores.gathered(s, 1, 9, plot, Map.of("minecraft:oak_log", 3), 60);
		assertEquals(EventTypes.GOODS_GATHERED, g.type());
	}
}
