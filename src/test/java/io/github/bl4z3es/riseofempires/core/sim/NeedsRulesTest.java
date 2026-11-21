package io.github.bl4z3es.riseofempires.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Condition;
import io.github.bl4z3es.riseofempires.core.model.Needs;
import io.github.bl4z3es.riseofempires.core.model.Profession;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import io.github.bl4z3es.riseofempires.core.model.Stockpile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NeedsRulesTest {
	private static final long DAY = GameTime.TICKS_PER_DAY;
	private static final long SID = 1;
	private static final FoodValues FOOD = FoodValues.defaults();

	private Event founded;

	/** A settlement with {@code people} residents (ids 1..people) and a store of the given food. */
	private SimState world(long seed, int people, Map<String, Integer> food) {
		SimState s = new SimState(seed);
		founded = s.ledger().append(EventDraft.of(EventTypes.SETTLEMENT_FOUNDED, 0));
		List<Long> ids = new ArrayList<>();
		for (long i = 1; i <= people; i++) ids.add(i);
		s.put(new Settlement(SID, "Camp", "player", new Place("minecraft:overworld", 0, 64, 0),
				Balance.STARTING_CLAIM_RADIUS_BLOCKS, ids, null, founded.id()), founded);
		for (long i = 1; i <= people; i++) {
			s.put(new Character(i, "P" + i, "calm", "bread", Profession.NONE, List.of(), Needs.MET, Condition.WELL, SID), founded);
		}
		Event counted = s.ledger().append(EventDraft.of(EventTypes.STOCKPILE_COUNTED, 0).cause(founded.id()));
		s.put(new Stockpile(SID, food, Map.of()), counted);
		s.noteStockEvent(SID, counted);
		return s;
	}

	private static Condition condition(SimState s, long id) { return s.character(id).orElseThrow().condition(); }

	private static Needs needs(SimState s, long id) { return s.character(id).orElseThrow().needs(); }

	@Test
	void slotMaths() {
		assertEquals(1000, NeedsRules.mealSlotTime(0));
		assertEquals(12000, NeedsRules.mealSlotTime(1));
		assertEquals(DAY + 1000, NeedsRules.mealSlotTime(2));
		assertEquals(-1, NeedsRules.mealSlotAt(999));
		assertEquals(0, NeedsRules.mealSlotAt(1000));
		assertEquals(0, NeedsRules.mealSlotAt(11999));
		assertEquals(1, NeedsRules.mealSlotAt(12000));
		assertEquals(1, NeedsRules.mealSlotAt(DAY + 999));
		assertEquals(2, NeedsRules.mealSlotAt(DAY + 1000));
		assertEquals(5, NeedsRules.mealSlotAt(2 * DAY + 13000));
	}

	@Test
	void mealCostRisesInWinter() {
		assertEquals(4, NeedsRules.mealCost(Season.SPRING));
		assertEquals(4, NeedsRules.mealCost(Season.AUTUMN));
		assertEquals(5, NeedsRules.mealCost(Season.WINTER));
	}

	@Test
	void foodValues() {
		assertEquals(4, FOOD.quarters("minecraft:wheat"));
		assertEquals(0, FOOD.quarters("minecraft:stone"));
		assertTrue(FOOD.isFood("minecraft:bread"));
		assertTrue(!FOOD.isFood("minecraft:stone"));
		Stockpile p = new Stockpile(SID, Map.of("minecraft:wheat", 3, "minecraft:bread", 2, "minecraft:stone", 9), Map.of());
		assertEquals(3 * 4 + 2 * 12, FOOD.totalQuarters(p));
	}

	@Test
	void everyoneEatsAndWheatGoesDownByThreePerSlot() {
		SimState s = world(1, 3, Map.of("minecraft:wheat", 10));
		List<Event> events = NeedsRules.eatMeals(s, 1000, FOOD);
		assertEquals(1, events.size());
		assertEquals(EventTypes.MEAL_EATEN, events.get(0).type());
		assertEquals(7, s.stockpile(SID).total("minecraft:wheat"));
		assertTrue(events.get(0).causes().contains(founded.id()));
		NeedsRules.eatMeals(s, 12000, FOOD);
		assertEquals(4, s.stockpile(SID).total("minecraft:wheat"));
		for (long i = 1; i <= 3; i++) assertEquals(Needs.MET, needs(s, i));
	}

	@Test
	void breadFeedsThreeThroughTheLarder() {
		SimState s = world(1, 3, Map.of("minecraft:bread", 2));
		List<Event> events = NeedsRules.eatMeals(s, 1000, FOOD);
		assertEquals(1, events.size());
		assertEquals(1, s.stockpile(SID).total("minecraft:bread"));
		assertEquals("0", s.fact("larder.1").orElse(null));
	}

	@Test
	void leftoverLarderCarriesOver() {
		SimState s = world(1, 1, Map.of("minecraft:bread", 1));
		NeedsRules.eatMeals(s, 1000, FOOD);
		assertEquals("8", s.fact("larder.1").orElseThrow());
		NeedsRules.eatMeals(s, 12000, FOOD);
		assertEquals("4", s.fact("larder.1").orElseThrow());
		assertEquals(0, s.stockpile(SID).total("minecraft:bread"));
		assertEquals(Needs.MET, needs(s, 1));
	}

	@Test
	void winterMealCostsFiveQuarters() {
		SimState s = world(1, 1, Map.of("minecraft:wheat", 10));
		long winter = 18 * DAY + 1000;
		assertEquals(Season.WINTER, Calendar.season(winter));
		NeedsRules.eatMeals(s, winter, FOOD);
		assertEquals(8, s.stockpile(SID).total("minecraft:wheat"));
		assertEquals("3", s.fact("larder.1").orElseThrow());
	}

	@Test
	void lowestValueFoodIsEatenFirst() {
		SimState s = world(1, 1, Map.of("minecraft:bread", 1, "minecraft:wheat", 1, "minecraft:beetroot", 1));
		NeedsRules.eatMeals(s, 1000, FOOD);
		// beetroot (2) then wheat (4) cover 6 quarters, enough for a 4-quarter meal
		assertEquals(0, s.stockpile(SID).total("minecraft:beetroot"));
		assertEquals(0, s.stockpile(SID).total("minecraft:wheat"));
		assertEquals(1, s.stockpile(SID).total("minecraft:bread"));
	}

	/** One resident, no food. Returns the events from slot 0 through slot 7. */
	private List<Event> starve(SimState s) {
		List<Event> all = new ArrayList<>(NeedsRules.eatMeals(s, 1000, FOOD));
		all.addAll(NeedsRules.eatMeals(s, NeedsRules.mealSlotTime(7), FOOD));
		return all;
	}

	@Test
	void fullDeclineWithNoFood() {
		SimState s = world(3, 1, Map.of());
		List<Event> events = starve(s);
		List<String> steps = events.stream()
				.filter(e -> !e.type().equals(EventTypes.MEAL_MISSED))
				.map(e -> e.effects().get(0).detail()).toList();
		assertEquals(List.of("WELL->STRAINED", "STRAINED->WEAK", "WEAK->ILL"),
				steps.subList(0, 3));
		Event last = events.get(events.size() - 1);
		assertTrue(last.type().equals(EventTypes.CHARACTER_DIED) || last.type().equals(EventTypes.CHARACTER_LEFT));
		assertTrue(condition(s, 1).isGone());
		for (Event e : events) {
			if (e.type().equals(EventTypes.MEAL_MISSED)) continue;
			boolean leadsToFounding = s.ledger().causeChain(e.id()).stream()
					.anyMatch(c -> c.id() == founded.id());
			assertTrue(leadsToFounding, "event " + e.type() + " does not trace back to the founding");
		}
		// Once gone, nothing changes any more.
		int size = s.ledger().size();
		NeedsRules.eatMeals(s, NeedsRules.mealSlotTime(9), FOOD);
		assertEquals(size, s.ledger().size());
	}

	private String finalOutcome(long seed) {
		SimState s = world(seed, 1, Map.of());
		List<Event> events = starve(s);
		return events.get(events.size() - 1).type();
	}

	@Test
	void finalStepIsDeterministicAndBothOutcomesOccur() {
		Set<String> seen = new HashSet<>();
		for (long seed = 0; seed < 40; seed++) {
			String a = finalOutcome(seed);
			assertEquals(a, finalOutcome(seed));
			seen.add(a);
		}
		assertEquals(Set.of(EventTypes.CHARACTER_DIED, EventTypes.CHARACTER_LEFT), seen);
	}

	@Test
	void feedingAgainLowersMissedMealsAndTheConditionRecovers() {
		SimState s = world(1, 1, Map.of());
		NeedsRules.eatMeals(s, 1000, FOOD);
		NeedsRules.eatMeals(s, NeedsRules.mealSlotTime(4), FOOD);
		assertEquals(5, needs(s, 1).missedMeals());
		assertEquals(Condition.WEAK, condition(s, 1));

		Event counted = s.ledger().append(EventDraft.of(EventTypes.STOCKPILE_COUNTED, NeedsRules.mealSlotTime(4)).cause(founded.id()));
		s.put(new Stockpile(SID, Map.of("minecraft:wheat", 10), Map.of()), counted);
		s.noteStockEvent(SID, counted);

		NeedsRules.eatMeals(s, NeedsRules.mealSlotTime(5), FOOD);
		assertEquals(3, needs(s, 1).missedMeals());
		assertEquals(Condition.STRAINED, condition(s, 1));
		NeedsRules.eatMeals(s, NeedsRules.mealSlotTime(6), FOOD);
		assertEquals(1, needs(s, 1).missedMeals());
		assertEquals(Condition.WELL, condition(s, 1));
		NeedsRules.eatMeals(s, NeedsRules.mealSlotTime(7), FOOD);
		assertEquals(0, needs(s, 1).missedMeals());
	}

	@Test
	void roughNightsCountAndSleepingResets() {
		SimState s = world(1, 1, Map.of());
		NeedsRules.judgeNights(s, NeedsRules.nightTime(0), Set.of());
		assertEquals(1, needs(s, 1).nightsRough());
		List<Event> events = NeedsRules.judgeNights(s, NeedsRules.nightTime(3), Set.of());
		assertEquals(4, needs(s, 1).nightsRough());
		assertEquals(Condition.WEAK, condition(s, 1));
		assertTrue(events.stream().anyMatch(e -> e.type().equals(EventTypes.SLEPT_ROUGH)));

		List<Event> slept = NeedsRules.judgeNights(s, NeedsRules.nightTime(4), Set.of(1L));
		assertEquals(0, needs(s, 1).nightsRough());
		assertEquals(Condition.WELL, condition(s, 1));
		assertEquals(EventTypes.SLEPT, slept.get(0).type());
	}

	@Test
	void missedMealsAndRoughNightsAddUp() {
		SimState s = world(1, 1, Map.of());
		NeedsRules.eatMeals(s, 1000, FOOD);
		NeedsRules.judgeNights(s, NeedsRules.nightTime(0), Set.of());
		assertEquals(Condition.STRAINED, condition(s, 1)); // 1 missed meal + 1 rough night
	}

	@Test
	void noNightHasHappenedYet() {
		SimState s = world(1, 1, Map.of());
		assertTrue(NeedsRules.judgeNights(s, 17999, Set.of()).isEmpty());
	}

	@Test
	void catchUpProcessesEverySlot() {
		SimState s = world(1, 3, Map.of("minecraft:wheat", 20));
		s.setCursor("needs.meal", -1);
		List<Event> events = NeedsRules.eatMeals(s, 2 * DAY - 1, FOOD);
		assertEquals(4, events.size());
		assertEquals(8, s.stockpile(SID).total("minecraft:wheat"));
		assertEquals(3, s.cursor("needs.meal", -99));
	}

	@Test
	void outputFallsWithCondition() {
		assertEquals(1.0, NeedsRules.outputMultiplier(Condition.WELL));
		assertEquals(0.75, NeedsRules.outputMultiplier(Condition.STRAINED));
		assertEquals(0.5, NeedsRules.outputMultiplier(Condition.WEAK));
		assertEquals(0.0, NeedsRules.outputMultiplier(Condition.ILL));
		assertEquals(0.0, NeedsRules.outputMultiplier(Condition.DEAD));
		assertEquals(0.0, NeedsRules.outputMultiplier(Condition.LEFT));
	}
}
