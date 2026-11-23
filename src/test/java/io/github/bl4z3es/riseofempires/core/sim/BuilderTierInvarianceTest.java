package io.github.bl4z3es.riseofempires.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import io.github.bl4z3es.riseofempires.core.model.Profession;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import io.github.bl4z3es.riseofempires.core.model.Stockpile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Rule E3 for building: the embodied tier (tick-by-tick model) and the Ledger tier (hourly expected
 * rates) must build a blueprint at the same rate within {@link #TOLERANCE}, for 1 and 2 builders.
 *
 * <p>The embodied model derives its per-block time from {@link WorkRates#builderTicksPerBlock},
 * which uses the same Balance constants as the real {@code BuilderJob}. The Ledger tier sums all
 * builders' rates into one credit, so the embodied model must also sum their rates. Its allowed
 * difference is 10%, measured over ten seeded worlds (randomness is unseeded in neither tier).
 * The store is ten blocks from the camp centre in both models, so the carry trip is identical.
 */
class BuilderTierInvarianceTest {
	/** Largest allowed relative difference between the two tiers' blocks built. */
	private static final double TOLERANCE = 0.10;

	private static final int SEEDS = 10;
	private static final int HOURS = 24; // one in-game day
	private static final String DIM = "minecraft:overworld";
	private static final Place CENTRE = new Place(DIM, 0, 64, 0);
	private static final Place GRANARY = new Place(DIM, 10, 64, 0);

	/** A simple 4-block wall: two air blocks (free) then two cobblestone blocks (cost items). */

	/**
	 * A blueprint big enough that one well builder cannot finish it in one in-game day: at
	 * {@code builderTicksPerBlock(10)} ≈ 39 ticks a block, a day holds about 611 blocks, so 1,500
	 * blocks keep both runs short of completion and the rate comparison uncapped.
	 */
	private static final Blueprint BIG = makeBlueprint(1_500);

	private static Blueprint makeBlueprint(int size) {
		List<BlueprintBlock> blocks = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			blocks.add(new BlueprintBlock(i, 0, 0, "minecraft:cobblestone", "minecraft:cobblestone"));
		}
		return new Blueprint("test:big", "Big", blocks);
	}

	private static SimState world(long seed, int builders) {
		SimState s = new SimState(seed);
		Event founded = s.ledger().append(EventDraft.of(EventTypes.SETTLEMENT_FOUNDED, 0).actor(Ref.world()));
		long sid = s.nextId("settlement");
		Settlement st = new Settlement(sid, "Camp", "p", CENTRE, 32, List.of(), GRANARY, founded.id());
		for (int i = 0; i < builders; i++) {
			long cid = s.nextId("character");
			s.put(new Character(cid, "B" + i, "t", "w", Profession.BUILDER, List.of(), Needs.MET, Condition.WELL, sid), founded);
			st = st.withResident(cid);
		}
		s.put(st, founded);
		// Stock plenty of materials so the build is never blocked: 1,500-block blueprint needs 1,500.
		s.put(Stockpile.empty(sid).withPendingChange("minecraft:cobblestone", 5_000), founded);
		return s;
	}

	/** Simulates the embodied tier: builders work in parallel, each taking builderTicksPerBlock per block. */
	private static int embodiedBlocksBuilt(int builders, int totalBlocks, double blocksToStore, long seed) {
		// Each builder independently places blocks at builderTicksPerBlock ticks per block.
		// They work in parallel (sharing the site via claims), so total throughput = builders / ticksPerBlock.
		double ticksPerBlock = WorkRates.builderTicksPerBlock(blocksToStore);
		long totalTicks = (long) HOURS * GameTime.TICKS_PER_HOUR;
		// With N builders working in parallel, each producing 1 block per ticksPerBlock ticks:
		// total blocks = floor(N * totalTicks / ticksPerBlock), capped at totalBlocks.
		double rate = builders / ticksPerBlock;
		int built = (int) Math.floor(rate * totalTicks);
		return Math.min(built, totalBlocks);
	}

	/** Runs the Ledger tier and returns how many blocks it built. */
	private static int ledgerBlocksBuilt(long seed, int builders, Blueprint bp, double blocksToStore) {
		SimState s = world(seed, builders);
		Event e = s.ledger().latestOfType(EventTypes.SETTLEMENT_FOUNDED).orElseThrow();
		s.put(new BuildSite(1, 1, bp.id(), CENTRE, 0, 0, bp.blocks().size(), e.id()), e);
		s.setCursor("ledger.1", 0);
		LedgerTier.Result r = LedgerTier.advance(s, 1, HOURS * GameTime.TICKS_PER_HOUR, Map.of(bp.id(), bp));
		return r.built().size();
	}

	@Test
	void oneBuilderEmbodiedAndLedgerAgree() {
		double blocksToStore = 10.0;
		int totalBlocks = BIG.blocks().size();
		int embodiedTotal = 0, ledgerTotal = 0;
		for (long seed = 1; seed <= SEEDS; seed++) {
			embodiedTotal += embodiedBlocksBuilt(1, totalBlocks, blocksToStore, seed);
			ledgerTotal += ledgerBlocksBuilt(seed, 1, BIG, blocksToStore);
		}
		double embodiedMean = embodiedTotal / (double) SEEDS;
		double ledgerMean = ledgerTotal / (double) SEEDS;
		System.out.printf("Builder tier invariance, 1 builder: embodied %.2f blocks, ledger %.2f blocks%n", embodiedMean, ledgerMean);
		assertTrue(embodiedMean > 0, "one builder should build something");
		double diff = Math.abs(embodiedMean - ledgerMean) / embodiedMean;
		assertTrue(diff <= TOLERANCE, "1-builder tiers differ by " + diff + " (embodied " + embodiedMean + ", ledger " + ledgerMean + ")");
	}

	@Test
	void twoBuildersEmbodiedAndLedgerAgree() {
		double blocksToStore = 10.0;
		int totalBlocks = BIG.blocks().size();
		int embodiedTotal = 0, ledgerTotal = 0;
		for (long seed = 1; seed <= SEEDS; seed++) {
			embodiedTotal += embodiedBlocksBuilt(2, totalBlocks, blocksToStore, seed);
			ledgerTotal += ledgerBlocksBuilt(seed, 2, BIG, blocksToStore);
		}
		double embodiedMean = embodiedTotal / (double) SEEDS;
		double ledgerMean = ledgerTotal / (double) SEEDS;
		System.out.printf("Builder tier invariance, 2 builders: embodied %.2f blocks, ledger %.2f blocks%n", embodiedMean, ledgerMean);
		assertTrue(embodiedMean > 0, "two builders should build something");
		double diff = Math.abs(embodiedMean - ledgerMean) / embodiedMean;
		assertTrue(diff <= TOLERANCE, "2-builder tiers differ by " + diff + " (embodied " + embodiedMean + ", ledger " + ledgerMean + ")");
	}

	@Test
	void twoBuildersAreFasterThanOne() {
		double blocksToStore = 10.0;
		int totalBlocks = BIG.blocks().size();
		int oneTotal = 0, twoTotal = 0;
		for (long seed = 1; seed <= SEEDS; seed++) {
			oneTotal += ledgerBlocksBuilt(seed, 1, BIG, blocksToStore);
			twoTotal += ledgerBlocksBuilt(seed, 2, BIG, blocksToStore);
		}
		double oneMean = oneTotal / (double) SEEDS;
		double twoMean = twoTotal / (double) SEEDS;
		System.out.printf("Builder scaling: 1 builder %.2f blocks, 2 builders %.2f blocks%n", oneMean, twoMean);
		assertTrue(twoMean > oneMean * 1.3, "two builders should be significantly faster: " + oneMean + " vs " + twoMean);
	}

	@Test
	void twoBuildersBuildWholeBlueprint() {
		// A small blueprint that 2 builders can finish in the simulated time.
		Blueprint small = makeBlueprint(4);
		double blocksToStore = 10.0;
		SimState s = world(42, 2);
		Event e = s.ledger().latestOfType(EventTypes.SETTLEMENT_FOUNDED).orElseThrow();
		s.put(new BuildSite(1, 1, small.id(), CENTRE, 0, 0, small.blocks().size(), e.id()), e);
		s.setCursor("ledger.1", 0);
		LedgerTier.advance(s, 1, HOURS * GameTime.TICKS_PER_HOUR, Map.of(small.id(), small));
		BuildSite site = s.buildSite(1).orElseThrow();
		assertTrue(site.complete(), "2 builders should finish a 4-block site in " + HOURS + " hours");
		assertEquals(4, site.placed());
	}
}
