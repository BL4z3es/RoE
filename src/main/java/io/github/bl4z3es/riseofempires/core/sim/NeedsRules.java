package io.github.bl4z3es.riseofempires.core.sim;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Condition;
import io.github.bl4z3es.riseofempires.core.model.Needs;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import io.github.bl4z3es.riseofempires.core.model.Stockpile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Food and rest: what people eat, how they sleep, and what hunger and rough nights do to them. */
public final class NeedsRules {
	private NeedsRules() {}

	private static final String MEAL_CURSOR = "needs.meal";
	private static final String NIGHT_CURSOR = "needs.night";
	private static final String LARDER_PREFIX = "larder.";
	private static final long UNSET = Long.MIN_VALUE;

	// Meal slots

	/** World time of a meal slot. Slot n is meal n mod M of day n div M. */
	public static long mealSlotTime(long slot) {
		int m = Balance.MEAL_TIMES_OF_DAY.length;
		long day = Math.floorDiv(slot, (long) m);
		int k = (int) Math.floorMod(slot, (long) m);
		return day * GameTime.TICKS_PER_DAY + Balance.MEAL_TIMES_OF_DAY[k];
	}

	/** Newest slot whose time is at or before the given time, or -1 if none has happened yet. */
	public static long mealSlotAt(long worldTime) {
		int m = Balance.MEAL_TIMES_OF_DAY.length;
		long day = Calendar.day(worldTime);
		long tod = GameTime.timeOfDay(worldTime);
		long slot = day * m - 1;
		for (int k = 0; k < m; k++) if (Balance.MEAL_TIMES_OF_DAY[k] <= tod) slot = day * m + k;
		return Math.max(slot, -1);
	}

	/** Quarter-meals one meal costs in a season. */
	public static int mealCost(Season s) {
		double cost = Balance.QUARTERS_PER_MEAL;
		if (s == Season.WINTER) cost *= Balance.WINTER_FOOD_MULTIPLIER;
		return (int) Math.round(cost);
	}

	public static double outputMultiplier(Condition c) {
		return switch (c) {
			case WELL -> Balance.OUTPUT_WELL;
			case STRAINED -> Balance.OUTPUT_STRAINED;
			case WEAK -> Balance.OUTPUT_WEAK;
			case ILL -> Balance.OUTPUT_ILL;
			case DEAD, LEFT -> 0.0;
		};
	}

	// Meals

	/** Feeds every settlement for each meal slot not yet processed, up to the slot at {@code now}. */
	public static List<Event> eatMeals(SimState s, long now, FoodValues food) {
		List<Event> out = new ArrayList<>();
		long newest = mealSlotAt(now);
		long cursor = s.cursor(MEAL_CURSOR, UNSET);
		long first = cursor == UNSET ? newest : cursor + 1;
		for (long slot = first; slot <= newest; slot++) {
			if (slot < 0) continue;
			for (Settlement settlement : List.copyOf(s.settlements())) {
				mealForSettlement(s, settlement, slot, food, out);
			}
			s.setCursor(MEAL_CURSOR, slot);
		}
		return out;
	}

	private static void mealForSettlement(SimState s, Settlement settlement, long slot, FoodValues food, List<Event> out) {
		long sid = settlement.id();
		List<Character> residents = s.residents(sid);
		if (residents.isEmpty()) return;
		long time = mealSlotTime(slot);
		int cost = mealCost(Calendar.season(time));
		String larderKey = LARDER_PREFIX + sid;
		int larder = s.fact(larderKey).map(Integer::parseInt).orElse(0);
		Stockpile stock = s.stockpile(sid);
		Map<String, Integer> taken = new TreeMap<>();
		List<Character> eaters = new ArrayList<>();
		List<Character> missers = new ArrayList<>();

		for (Character c : residents) {
			while (larder < cost) {
				String item = cheapestFood(stock, food);
				if (item == null) break;
				stock = stock.withPendingChange(item, -1);
				taken.merge(item, 1, Integer::sum);
				larder += food.quarters(item);
			}
			if (larder >= cost) {
				larder -= cost;
				eaters.add(c);
			} else {
				missers.add(c);
			}
		}

		long lastStock = s.lastStockEvent(sid);
		Event eaten = null;
		Event missed = null;
		boolean stockWritten = false;

		if (!eaters.isEmpty()) {
			EventDraft d = EventDraft.of(EventTypes.MEAL_EATEN, time).at(settlement.centre());
			for (Character c : eaters) d.actor(Ref.character(c.id()));
			d.actor(Ref.settlement(sid));
			if (lastStock > 0) d.cause(lastStock);
			d.cause(settlement.foundedEventId());
			for (Map.Entry<String, Integer> e : taken.entrySet()) {
				d.effect(Ref.settlement(sid), "stockpile " + e.getKey(), "-" + e.getValue());
			}
			for (Character c : eaters) {
				d.effect(Ref.character(c.id()), "missed_meals", Integer.toString(forgiven(c)));
			}
			eaten = s.ledger().append(d);
			out.add(eaten);
			for (Character c : eaters) s.put(c.withNeeds(new Needs(forgiven(c), c.needs().nightsRough())), eaten);
			if (!taken.isEmpty()) {
				s.put(stock, eaten);
				s.noteStockEvent(sid, eaten);
				stockWritten = true;
			}
		}

		if (!missers.isEmpty()) {
			EventDraft d = EventDraft.of(EventTypes.MEAL_MISSED, time).at(settlement.centre());
			for (Character c : missers) d.actor(Ref.character(c.id()));
			d.actor(Ref.settlement(sid));
			if (eaten != null) d.cause(eaten.id());
			else if (lastStock > 0) d.cause(lastStock);
			else d.cause(settlement.foundedEventId());
			// Food taken into the larder but not enough for a meal still left the store.
			if (!stockWritten) {
				for (Map.Entry<String, Integer> e : taken.entrySet()) {
					d.effect(Ref.settlement(sid), "stockpile " + e.getKey(), "-" + e.getValue());
				}
			}
			for (Character c : missers) {
				d.effect(Ref.character(c.id()), "missed_meals", Integer.toString(c.needs().missedMeals() + 1));
			}
			missed = s.ledger().append(d);
			out.add(missed);
			for (Character c : missers) s.put(c.withNeeds(new Needs(c.needs().missedMeals() + 1, c.needs().nightsRough())), missed);
			if (!stockWritten && !taken.isEmpty()) {
				s.put(stock, missed);
				s.noteStockEvent(sid, missed);
			}
		}

		Event last = missed != null ? missed : eaten;
		s.putFact(larderKey, Integer.toString(larder), last);

		for (Character c : eaters) out.addAll(applyCondition(s, c.id(), eaten, time));
		for (Character c : missers) out.addAll(applyCondition(s, c.id(), missed, time));
	}

	private static int forgiven(Character c) {
		return Math.max(0, c.needs().missedMeals() - Balance.MISSED_MEALS_FORGIVEN_PER_MEAL);
	}

	/** The food in the store worth the fewest quarters, ties broken by item id; null if there is none. */
	private static String cheapestFood(Stockpile stock, FoodValues food) {
		String best = null;
		int bestQ = Integer.MAX_VALUE;
		for (Map.Entry<String, Integer> e : food.all().entrySet()) {
			if (stock.total(e.getKey()) > 0 && e.getValue() < bestQ) {
				best = e.getKey();
				bestQ = e.getValue();
			}
		}
		return best;
	}

	// Nights

	/** World time of a night's judgement. */
	public static long nightTime(long night) { return night * GameTime.TICKS_PER_DAY + Balance.NIGHT_TIME_OF_DAY; }

	/** Judges every night not yet processed, up to the newest night at or before {@code now}. */
	public static List<Event> judgeNights(SimState s, long now, Set<Long> sleptInBed) {
		List<Event> out = new ArrayList<>();
		long newest = Math.floorDiv(now - Balance.NIGHT_TIME_OF_DAY, GameTime.TICKS_PER_DAY);
		if (newest < 0) return out;
		long cursor = s.cursor(NIGHT_CURSOR, UNSET);
		long first = cursor == UNSET ? newest : cursor + 1;
		for (long night = first; night <= newest; night++) {
			long time = nightTime(night);
			for (Settlement settlement : List.copyOf(s.settlements())) {
				nightForSettlement(s, settlement, time, sleptInBed, out);
			}
			s.setCursor(NIGHT_CURSOR, night);
		}
		return out;
	}

	private static void nightForSettlement(SimState s, Settlement settlement, long time, Set<Long> sleptInBed, List<Event> out) {
		List<Character> residents = s.residents(settlement.id());
		if (residents.isEmpty()) return;
		List<Character> slept = new ArrayList<>();
		List<Character> rough = new ArrayList<>();
		for (Character c : residents) (sleptInBed.contains(c.id()) ? slept : rough).add(c);

		Event sleptEvent = null;
		Event roughEvent = null;
		if (!slept.isEmpty()) {
			sleptEvent = nightEvent(s, settlement, EventTypes.SLEPT, time, slept, false);
			for (Character c : slept) s.put(c.withNeeds(new Needs(c.needs().missedMeals(), 0)), sleptEvent);
			out.add(sleptEvent);
		}
		if (!rough.isEmpty()) {
			roughEvent = nightEvent(s, settlement, EventTypes.SLEPT_ROUGH, time, rough, true);
			for (Character c : rough) s.put(c.withNeeds(new Needs(c.needs().missedMeals(), c.needs().nightsRough() + 1)), roughEvent);
			out.add(roughEvent);
		}
		for (Character c : slept) out.addAll(applyCondition(s, c.id(), sleptEvent, time));
		for (Character c : rough) out.addAll(applyCondition(s, c.id(), roughEvent, time));
	}

	private static Event nightEvent(SimState s, Settlement settlement, String type, long time, List<Character> group, boolean rough) {
		EventDraft d = EventDraft.of(type, time).at(settlement.centre());
		for (Character c : group) d.actor(Ref.character(c.id()));
		d.actor(Ref.settlement(settlement.id()));
		d.cause(settlement.foundedEventId());
		for (Character c : group) {
			int value = rough ? c.needs().nightsRough() + 1 : 0;
			d.effect(Ref.character(c.id()), "nights_rough", Integer.toString(value));
		}
		return s.ledger().append(d);
	}

	// Condition

	/**
	 * Sets a character's condition from their unmet needs and writes the event if it changed.
	 * Returns the events written: none, a condition change, a death or a departure.
	 */
	public static List<Event> applyCondition(SimState s, long characterId, Event cause, long now) {
		Character c = s.character(characterId).orElse(null);
		if (c == null || c.condition().isGone()) return List.of();
		int d = c.needs().missedMeals() + c.needs().nightsRough();
		Condition next;
		String type;
		if (d >= Balance.GONE_AT) {
			boolean leaves = Dice.of(s.seed(), cause.id(), characterId).nextDouble() < Balance.LEAVE_CHANCE;
			next = leaves ? Condition.LEFT : Condition.DEAD;
			type = leaves ? EventTypes.CHARACTER_LEFT : EventTypes.CHARACTER_DIED;
		} else {
			next = d < Balance.STRAINED_AT ? Condition.WELL
					: d < Balance.WEAK_AT ? Condition.STRAINED
					: d < Balance.ILL_AT ? Condition.WEAK : Condition.ILL;
			type = EventTypes.CONDITION_CHANGED;
		}
		if (next == c.condition()) return List.of();

		EventDraft draft = EventDraft.of(type, now).actor(Ref.character(characterId)).cause(cause.id());
		Settlement home = s.settlement(c.settlementId()).orElse(null);
		if (home != null) {
			draft.at(home.centre()).actor(Ref.settlement(home.id()));
			for (Character other : s.residents(home.id())) {
				if (other.id() != characterId) draft.witness(Ref.character(other.id()));
			}
		}
		draft.effect(Ref.character(characterId), "condition", c.condition() + "->" + next);
		Event e = s.ledger().append(draft);
		s.put(c.withCondition(next), e);
		return List.of(e);
	}
}
