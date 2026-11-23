package io.github.bl4z3es.riseofempires.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Blueprint;
import io.github.bl4z3es.riseofempires.core.model.BlueprintBlock;
import io.github.bl4z3es.riseofempires.core.model.BuildSite;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Condition;
import io.github.bl4z3es.riseofempires.core.model.Needs;
import io.github.bl4z3es.riseofempires.core.model.Plot;
import io.github.bl4z3es.riseofempires.core.model.Profession;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import io.github.bl4z3es.riseofempires.core.model.Stockpile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Rule E3 for farming: the embodied tier (a tick-by-tick model) and the Ledger tier (hourly expected
 * rates) must produce the same wheat within {@link #TOLERANCE}.
 *
 * <p>Food EATEN is handled by NeedsRules in both tiers identically (same code), so this test only
 * compares production.
 */
class TierInvarianceTest {
	/** Largest allowed relative difference between the two tiers' mean wheat. */
	private static final double TOLERANCE = 0.10;

	private static final int SEEDS = 20;
	private static final int DAYS = 10;
	private static final String DIM = "minecraft:overworld";
	private static final Place CENTRE = new Place(DIM, 0, 64, 0);
	private static final Place GRANARY = new Place(DIM, 0, 64, 0);

	private static List<Plot> grid() {
		List<Plot> plots = new ArrayList<>();
		for (int x = 0; x < 4; x++) {
			for (int z = 0; z < 6; z++) plots.add(new Plot(new Place(DIM, 10 + x, 64, z), Farming.WHEAT, 0));
		}
		return plots;
	}

	private static SimState world(long seed, boolean granary, Profession... professions) {
		SimState s = new SimState(seed);
		Event founded = s.ledger().append(EventDraft.of(EventTypes.SETTLEMENT_FOUNDED, 0).actor(Ref.world()));
		long sid = s.nextId("settlement");
		Settlement st = new Settlement(sid, "Camp", "p", CENTRE, 32, List.of(), granary ? GRANARY : null, founded.id());
		for (Profession p : professions) {
			long cid = s.nextId("character");
			s.put(new Character(cid, "W" + cid, "t", "w", p, List.of(), Needs.MET, Condition.WELL, sid), founded);
			st = st.withResident(cid);
		}
		s.put(st, founded);
		s.putFarm(sid, grid(), founded);
		return s;
	}

	private static long wheat(SimState s, long sid) { return s.stockpile(sid).total(Farming.WHEAT); }

	private static double meanLedgerWheat(long fromDay, int days, boolean granary) {
		long total = 0;
		for (long seed = 1; seed <= SEEDS; seed++) {
			SimState s = world(seed, granary, Profession.FARMER);
			s.setCursor("ledger.1", Calendar.dayStart(fromDay));
			LedgerTier.advance(s, 1, Calendar.dayStart(fromDay + days), Map.of());
			total += wheat(s, 1);
		}
		return total / (double) SEEDS;
	}

	@Test
	void embodiedAndLedgerTiersAgreeOnWheat() {
		List<EmbodiedFarmModel.Pos> positions = grid().stream()
				.map(p -> new EmbodiedFarmModel.Pos(p.pos().x(), p.pos().z())).toList();
		long embodied = 0;
		for (long seed = 1; seed <= SEEDS; seed++) {
			embodied += new EmbodiedFarmModel(positions, new EmbodiedFarmModel.Pos(0, 0), 1.0).runDays(DAYS, seed);
		}
		double embodiedMean = embodied / (double) SEEDS;
		double ledgerMean = meanLedgerWheat(0, DAYS, true);
		System.out.printf("Tier invariance, %d days, %d seeds: embodied mean %.2f wheat, ledger mean %.2f wheat%n",
				DAYS, SEEDS, embodiedMean, ledgerMean);
		assertTrue(embodiedMean > 20, "embodied tier should produce a real harvest");
		double diff = Math.abs(embodiedMean - ledgerMean) / embodiedMean;
		assertTrue(diff <= TOLERANCE, "tiers differ by " + diff + " (embodied " + embodiedMean + ", ledger " + ledgerMean + ")");
	}

	@Test
	void winterGivesNoGrowthInEitherTier() {
		long winterStart = Calendar.seasonStart(3);
		assertEquals(Season.WINTER, Calendar.season(winterStart));
		assertEquals(0.0, Farming.stageChance(1000, Season.WINTER));
		assertEquals(0.0, meanLedgerWheat(winterStart / GameTime.TICKS_PER_DAY, 6, true));
		SimState s = world(1, true, Profession.FARMER);
		s.setCursor("ledger.1", winterStart);
		LedgerTier.advance(s, 1, winterStart + 6 * GameTime.TICKS_PER_DAY, Map.of());
		assertTrue(s.farm(1).stream().allMatch(p -> p.age() == 0));
		assertEquals(0, s.ledger().all().stream().filter(e -> e.type().equals(EventTypes.LEDGER_WORK)).count());
	}

	@Test
	void winterEmbodiedModelGrowsNothing() {
		List<EmbodiedFarmModel.Pos> positions = grid().stream()
				.map(p -> new EmbodiedFarmModel.Pos(p.pos().x(), p.pos().z())).toList();
		long winterDay = Calendar.seasonStart(3) / GameTime.TICKS_PER_DAY;
		assertEquals(0, new EmbodiedFarmModel(positions, new EmbodiedFarmModel.Pos(0, 0), 1.0).runDays(winterDay, 6, 1));
	}

	@Test
	void noGranaryMeansNoProduction() {
		assertEquals(0.0, meanLedgerWheat(0, DAYS, false));
		SimState s = world(1, false, Profession.FARMER, Profession.GATHERER, Profession.BUILDER);
		s.setCursor("ledger.1", 0);
		LedgerTier.advance(s, 1, Calendar.dayStart(DAYS), Map.of());
		assertEquals(0, s.stockpile(1).totals().size());
	}

	@Test
	void ledgerTierIsDeterministic() {
		SimState a = world(7, true, Profession.FARMER, Profession.GATHERER);
		SimState b = world(7, true, Profession.FARMER, Profession.GATHERER);
		a.setCursor("ledger.1", 0);
		b.setCursor("ledger.1", 0);
		LedgerTier.advance(a, 1, Calendar.dayStart(5), Map.of());
		LedgerTier.advance(b, 1, Calendar.dayStart(5), Map.of());
		assertEquals(a.stockpile(1), b.stockpile(1));
		assertEquals(a.farm(1), b.farm(1));
		assertEquals(a.ledger().all(), b.ledger().all());
	}

	@Test
	void firstCallOnlySetsTheCursor() {
		SimState s = world(1, true, Profession.FARMER);
		LedgerTier.Result r = LedgerTier.advance(s, 1, 5500, Map.of());
		assertTrue(r.events().isEmpty());
		assertEquals(5000, s.cursor("ledger.1", -1));
	}

	@Test
	void buildersConsumeMaterialsAndStopWhenMissing() {
		Blueprint bp = new Blueprint("test:hut", "Hut", List.of(
				new BlueprintBlock(0, 0, 0, "minecraft:oak_planks", "minecraft:oak_planks"),
				new BlueprintBlock(1, 0, 0, "minecraft:oak_planks", "minecraft:oak_planks"),
				new BlueprintBlock(2, 0, 0, "minecraft:oak_planks", "minecraft:oak_planks"),
				new BlueprintBlock(3, 0, 0, "minecraft:air", "")));
		SimState s = world(3, true, Profession.BUILDER);
		Event e = s.ledger().latestOfType(EventTypes.SETTLEMENT_FOUNDED).orElseThrow();
		s.put(new BuildSite(1, 1, "test:hut", CENTRE, 0, 0, 4, e.id()), e);
		s.put(Stockpile.empty(1).withPendingChange("minecraft:oak_planks", 2), e);
		s.setCursor("ledger.1", 0);
		LedgerTier.Result r = LedgerTier.advance(s, 1, Calendar.dayStart(2), Map.of("test:hut", bp));
		// Two planks exist, so two blocks are built and the third (needs a plank) is not.
		assertEquals(2, s.buildSite(1).orElseThrow().placed());
		assertEquals(2, r.built().size());
		assertEquals(0, s.stockpile(1).total("minecraft:oak_planks"));
		assertFalse(s.buildSite(1).orElseThrow().complete());

		// With more planks the site finishes its blocks, including the free one, but is not marked complete here.
		Event e2 = s.ledger().latestOfType(EventTypes.LEDGER_WORK).orElseThrow();
		s.put(s.stockpile(1).withPendingChange("minecraft:oak_planks", 5), e2);
		LedgerTier.advance(s, 1, Calendar.dayStart(4), Map.of("test:hut", bp));
		assertEquals(4, s.buildSite(1).orElseThrow().placed());
		assertEquals(4, s.stockpile(1).total("minecraft:oak_planks"));
	}

	@Test
	void everyLedgerWorkEventTracesBackToTheFounding() {
		SimState s = world(5, true, Profession.FARMER, Profession.GATHERER);
		s.setCursor("ledger.1", 0);
		LedgerTier.Result r = LedgerTier.advance(s, 1, Calendar.dayStart(4), Map.of());
		assertFalse(r.events().isEmpty());
		Event founded = s.ledger().latestOfType(EventTypes.SETTLEMENT_FOUNDED).orElseThrow();
		for (Event e : r.events()) {
			assertEquals(EventTypes.LEDGER_WORK, e.type());
			assertTrue(s.ledger().causeChain(e.id()).contains(founded), "event " + e.id() + " does not reach the founding");
			assertTrue(e.causes().contains(founded.id()));
		}
	}
}
