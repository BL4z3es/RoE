package io.github.bl4z3es.riseofempires.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StateCodecTest {
	private static final Place HOME = new Place("minecraft:overworld", 10, 70, -20);

	@Test
	void everyRecordTypeRoundTrips() throws IOException {
		SimState s = new SimState(987654321L);
		Event e = s.ledger().append(EventDraft.of(EventTypes.WORLD_BEGAN, 5).actor(Ref.world()));

		Character c = new Character(1, "Ada", "calm", "a roof", Profession.BUILDER,
				List.of(new Memory(e.id(), "first night")), new Needs(1, 2), Condition.STRAINED, 1);
		Character gone = new Character(2, "Bo", "", "", Profession.NONE, List.of(), Needs.MET, Condition.DEAD, 0);
		Settlement t = new Settlement(1, "Ashfall", "uuid-1", HOME, 32, List.of(1L, 2L), new Place("minecraft:overworld", 12, 70, -20), e.id());
		Settlement bare = new Settlement(2, "Outpost", "uuid-2", HOME, 16, List.of(), null, e.id());
		Stockpile stock = new Stockpile(1, Map.of("minecraft:wheat", 12, "minecraft:bread", 3), Map.of("minecraft:wheat", -4));
		Raid raid = new Raid(1, 1, 6, e.id(), new Place("minecraft:overworld", 200, 64, 200), 1000, 2000, RaidStage.SIGHTED, 6);
		BuildSite site = new BuildSite(1, 1, Blueprint.FARM, HOME, 3, 4, 9, e.id());
		List<Plot> plots = List.of(new Plot(HOME, "minecraft:wheat", 5), new Plot(new Place("minecraft:overworld", 11, 70, -20), "minecraft:wheat", 0));

		s.put(c, e);
		s.put(gone, e);
		s.put(t, e);
		s.put(bare, e);
		s.put(stock, e);
		s.put(raid, e);
		s.put(site, e);
		s.putFarm(1, plots, e);
		s.putActOne(1, ActOutcome.SURVIVED, e);
		s.recordFounder("uuid-1", 1, e);
		s.putFact("hollow_ruin", "minecraft:overworld 5 60 5", e);
		s.nextId("character");
		s.nextId("character");
		s.nextId("settlement");
		s.setCursor("meal", 17);
		s.noteStockEvent(1, e);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StateCodec.write(out, s);
		SimState b = StateCodec.read(new ByteArrayInputStream(out.toByteArray()), s.ledger());

		assertEquals(s.seed(), b.seed());
		assertEquals(new ArrayList<>(s.characters()), new ArrayList<>(b.characters()));
		assertEquals(new ArrayList<>(s.settlements()), new ArrayList<>(b.settlements()));
		assertEquals(new ArrayList<>(s.stockpiles()), new ArrayList<>(b.stockpiles()));
		assertEquals(new ArrayList<>(s.raids()), new ArrayList<>(b.raids()));
		assertEquals(new ArrayList<>(s.buildSites()), new ArrayList<>(b.buildSites()));
		assertEquals(s.farms(), b.farms());
		assertEquals(2, b.farm(1).size());
		assertEquals(s.actOneOutcomes(), b.actOneOutcomes());
		assertEquals(s.foundedBy(), b.foundedBy());
		assertEquals(s.facts(), b.facts());
		assertEquals(s.counters(), b.counters());
		assertEquals(Map.of("character", 2L, "settlement", 1L), b.counters());
		assertEquals(s.cursors(), b.cursors());
		assertEquals(17L, b.cursor("meal", -1));
		assertEquals(e.id(), b.lastStockEvent(1));
		assertEquals(3, b.nextId("character"));
		assertEquals(null, b.settlement(2).orElseThrow().granary());
	}

	@Test
	void emptyStateRoundTrips() throws IOException {
		SimState s = new SimState(1L);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StateCodec.write(out, s);
		SimState b = StateCodec.read(new ByteArrayInputStream(out.toByteArray()), s.ledger());
		assertEquals(1L, b.seed());
		assertEquals(0, b.characters().size());
	}

	@Test
	void badMagicIsRefused() {
		byte[] junk = new byte[] {1, 2, 3, 4, 0, 0, 0, 1};
		assertThrows(IOException.class, () -> StateCodec.read(new ByteArrayInputStream(junk), new SimState(1L).ledger()));
	}
}
