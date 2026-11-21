package io.github.bl4z3es.riseofempires.core.sim;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Blueprint;
import io.github.bl4z3es.riseofempires.core.model.BuildSite;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Condition;
import io.github.bl4z3es.riseofempires.core.model.Plot;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import io.github.bl4z3es.riseofempires.core.model.Stockpile;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The off-camera tier (CONTEXT.md 3.10): a settlement's work, advanced one in-game hour at a time
 * with the expected rates the embodied workers would achieve (rules E3, E6).
 */
public final class LedgerTier {
	private LedgerTier() {}

	/** A blueprint block the Ledger tier built off-camera, to be placed in the world when the area loads. */
	public record BuiltBlock(long siteId, int index) {}

	public record Result(List<Event> events, List<BuiltBlock> built) {}

	// PROPOSED: not in Balance yet. Average items a farmer carries per harvested plot (wheat plus spare seeds).
	private static final double ITEMS_PER_PLOT = 2.7;

	private static final long UNSET = Long.MIN_VALUE;
	private static final String LOG = "minecraft:oak_log";
	private static final String STONE = "minecraft:cobblestone";

	public static Result advance(SimState s, long settlementId, long now, Map<String, Blueprint> blueprints) {
		List<Event> events = new ArrayList<>();
		List<BuiltBlock> built = new ArrayList<>();
		String cursorKey = "ledger." + settlementId;
		long cursor = s.cursor(cursorKey, UNSET);
		if (cursor == UNSET) {
			s.setCursor(cursorKey, Math.floorDiv(now, GameTime.TICKS_PER_HOUR) * GameTime.TICKS_PER_HOUR);
			return new Result(events, built);
		}
		if (s.settlement(settlementId).isEmpty()) return new Result(events, built);

		while (cursor + GameTime.TICKS_PER_HOUR <= now) {
			long t = cursor + GameTime.TICKS_PER_HOUR;
			hour(s, settlementId, t, blueprints, events, built);
			cursor = t;
			s.setCursor(cursorKey, cursor);
		}
		return new Result(events, built);
	}

	private static void hour(SimState s, long sid, long t, Map<String, Blueprint> blueprints,
			List<Event> events, List<BuiltBlock> built) {
		Settlement settlement = s.settlement(sid).orElseThrow();
		// The hour being worked is [t - 1000, t), so its season is the season of its first tick.
		Season season = Calendar.season(t - GameTime.TICKS_PER_HOUR);
		long seed = s.seed();

		// a. Crops grow.
		List<Plot> plots = new ArrayList<>(s.farm(sid));
		double chance = Farming.stageChance(GameTime.TICKS_PER_HOUR, season);
		int grown = 0;
		for (int i = 0; i < plots.size(); i++) {
			Plot p = plots.get(i);
			if (p.age() < Farming.WHEAT_MAX_AGE && Dice.of(seed, sid, t, i).nextDouble() < chance) {
				plots.set(i, p.withAge(p.age() + 1));
				grown++;
			}
		}

		Stockpile stock = s.stockpile(sid);
		boolean stockChanged = false;
		Map<String, Integer> itemDelta = new TreeMap<>();
		int harvested = 0;
		int blocksBuilt = 0;
		Set<Ref> workers = new LinkedHashSet<>();
		Map<String, String> facts = new TreeMap<>();
		BuildSite changedSite = null;

		// b. Nowhere to store anything without a granary.
		if (settlement.granary() != null) {
			double blocksToStore = distanceToStore(settlement, plots);
			List<Character> farmers = new ArrayList<>(), gatherers = new ArrayList<>(), builders = new ArrayList<>();
			for (Character c : s.residents(sid)) {
				if (output(c.condition()) <= 0) continue;
				switch (c.profession()) {
					case FARMER -> farmers.add(c);
					case GATHERER -> gatherers.add(c);
					case BUILDER -> builders.add(c);
					default -> { }
				}
			}

			// c. Farmers harvest mature plots.
			String farmKey = "credit.farm." + sid;
			double farmCredit = credit(s, farmKey);
			double farmRate = GameTime.TICKS_PER_HOUR / WorkRates.farmerTicksPerPlot(blocksToStore, ITEMS_PER_PLOT);
			for (Character c : farmers) farmCredit += output(c.condition()) * farmRate;
			while (farmCredit >= 1) {
				int idx = firstMature(plots);
				if (idx < 0) break;
				int seeds = Farming.seedsFromHarvest(Dice.of(seed, sid, t, idx, 1));
				plots.set(idx, plots.get(idx).withAge(0));
				stock = stock.withPendingChange(Farming.WHEAT, 1);
				itemDelta.merge(Farming.WHEAT, 1, Integer::sum);
				if (seeds > 1) {
					stock = stock.withPendingChange(Farming.WHEAT_SEEDS, seeds - 1);
					itemDelta.merge(Farming.WHEAT_SEEDS, seeds - 1, Integer::sum);
				}
				stockChanged = true;
				harvested++;
				farmCredit -= 1;
				for (Character f : farmers) workers.add(Ref.character(f.id()));
			}
			if (firstMature(plots) < 0) farmCredit = Math.min(farmCredit, 1);
			facts.put(farmKey, Double.toString(farmCredit));

			// d. Gatherers fetch logs and stone, alternately.
			String gatherKey = "credit.gather." + sid;
			String nextKey = "gather.next." + sid;
			double gatherCredit = credit(s, gatherKey);
			long next = (long) credit(s, nextKey);
			double gatherRate = GameTime.TICKS_PER_HOUR / WorkRates.gathererTicksPerItem(blocksToStore);
			for (Character c : gatherers) gatherCredit += output(c.condition()) * gatherRate;
			boolean gathered = false;
			while (gatherCredit >= 1) {
				String item = next == 0 ? LOG : STONE;
				next = 1 - next;
				stock = stock.withPendingChange(item, 1);
				itemDelta.merge(item, 1, Integer::sum);
				stockChanged = true;
				gathered = true;
				gatherCredit -= 1;
			}
			if (gathered) for (Character g : gatherers) workers.add(Ref.character(g.id()));
			facts.put(gatherKey, Double.toString(gatherCredit));
			facts.put(nextKey, Long.toString(next));

			// e. Builders place blocks of the oldest unfinished site.
			String buildKey = "credit.build." + sid;
			double buildCredit = credit(s, buildKey);
			double buildRate = GameTime.TICKS_PER_HOUR / WorkRates.builderTicksPerBlock(blocksToStore);
			for (Character c : builders) buildCredit += output(c.condition()) * buildRate;
			BuildSite site = null;
			for (BuildSite b : s.buildSites()) {
				if (b.settlementId() == sid && !b.complete()) { site = b; break; }
			}
			Blueprint bp = site == null ? null : blueprints.get(site.blueprintId());
			if (bp == null) {
				buildCredit = Math.min(buildCredit, 1);
			} else {
				while (buildCredit >= 1 && !site.complete()) {
					String item = bp.blocks().get(site.placed()).item();
					if (!item.isEmpty()) {
						if (stock.total(item) < 1) { buildCredit = Math.min(buildCredit, 1); break; }
						stock = stock.withPendingChange(item, -1);
						itemDelta.merge(item, -1, Integer::sum);
						stockChanged = true;
					}
					built.add(new BuiltBlock(site.id(), site.placed()));
					site = site.withPlaced(site.placed() + 1);
					changedSite = site;
					blocksBuilt++;
					buildCredit -= 1;
					for (Character b : builders) workers.add(Ref.character(b.id()));
				}
				if (site.complete()) buildCredit = Math.min(buildCredit, 1);
			}
			facts.put(buildKey, Double.toString(buildCredit));
		}

		// f. One event for the hour, only if something changed.
		if (grown == 0 && !stockChanged && harvested == 0 && blocksBuilt == 0) return;
		EventDraft d = EventDraft.of(EventTypes.LEDGER_WORK, t)
				.at(settlement.centre())
				.actor(Ref.settlement(sid))
				.actors(new ArrayList<>(workers));
		long lastStock = s.lastStockEvent(sid);
		if (lastStock > 0) d.cause(lastStock);
		if (settlement.foundedEventId() > 0) d.cause(settlement.foundedEventId());
		Ref self = Ref.settlement(sid);
		if (grown > 0) d.effect(self, "crops_grown", "+" + grown);
		for (Map.Entry<String, Integer> e : itemDelta.entrySet()) {
			if (e.getValue() != 0) d.effect(self, e.getKey(), (e.getValue() > 0 ? "+" : "") + e.getValue());
		}
		if (harvested > 0) d.effect(self, "plots_harvested", "+" + harvested);
		if (blocksBuilt > 0) d.effect(self, "blocks_built", "+" + blocksBuilt);
		Event e = s.ledger().append(d);
		events.add(e);

		if (stockChanged) {
			s.put(stock, e);
			s.noteStockEvent(sid, e);
		}
		s.putFarm(sid, plots, e);
		if (changedSite != null) s.put(changedSite, e);
		for (Map.Entry<String, String> f : facts.entrySet()) s.putFact(f.getKey(), f.getValue(), e);
	}

	private static double credit(SimState s, String key) {
		return s.fact(key).map(Double::parseDouble).orElse(0.0);
	}

	private static int firstMature(List<Plot> plots) {
		for (int i = 0; i < plots.size(); i++) if (plots.get(i).age() >= Farming.WHEAT_MAX_AGE) return i;
		return -1;
	}

	private static double distanceToStore(Settlement settlement, List<Plot> plots) {
		Place g = settlement.granary();
		double tx, tz;
		if (plots.isEmpty()) {
			tx = settlement.centre().x();
			tz = settlement.centre().z();
		} else {
			double sx = 0, sz = 0;
			for (Plot p : plots) { sx += p.pos().x(); sz += p.pos().z(); }
			tx = sx / plots.size();
			tz = sz / plots.size();
		}
		return Math.hypot(g.x() - tx, g.z() - tz);
	}

	// Work output by condition. NeedsRules owns the shared version once it lands.
	private static double output(Condition c) {
		return switch (c) {
			case WELL -> Balance.OUTPUT_WELL;
			case STRAINED -> Balance.OUTPUT_STRAINED;
			case WEAK -> Balance.OUTPUT_WEAK;
			case ILL -> Balance.OUTPUT_ILL;
			case DEAD, LEFT -> 0.0;
		};
	}
}
