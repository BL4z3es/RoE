package io.github.bl4z3es.riseofempires.core.sim.camp;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.model.Stockpile;
import io.github.bl4z3es.riseofempires.core.sim.Calendar;
import io.github.bl4z3es.riseofempires.core.sim.CalendarSystem;
import io.github.bl4z3es.riseofempires.core.sim.Effects;
import io.github.bl4z3es.riseofempires.core.sim.SimSystem;
import io.github.bl4z3es.riseofempires.core.sim.Simulation;
import io.github.bl4z3es.riseofempires.core.sim.WorldState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Food and rest (task M1-15). Runs the same in every tier: it is bookkeeping, and the Minecraft
 * layer mirrors its stockpile changes into the real containers.
 *
 * <p>Hunger counts meals missed (one full meal takes {@link Balance#MEAL_HUNGER_RECOVERY} off).
 * Fatigue counts nights without a bed (one night in a bed clears it). Unmet needs step a person
 * down one stage at a time, each stage its own event caused by the one before:
 * well → {@link #WEAKENED} (less work) → {@link #FELL_ILL} (much less work) → death, or leaving
 * for those whose trait gives up on a failing camp.
 */
public final class Needs implements SimSystem {
    public static final String ID = "needs";

    public static final String MEAL_SERVED = "meal_served";
    public static final String WENT_HUNGRY = "went_hungry";
    public static final String FOOD_RAN_OUT = "food_ran_out";
    public static final String SLEPT = "slept";
    public static final String SLEPT_ROUGH = "slept_rough";
    public static final String BEDS_COUNTED = "beds_counted";
    public static final String WEAKENED = "weakened";
    public static final String FELL_ILL = "fell_ill";
    public static final String RECOVERED = "recovered";

    /** Character stat: meals missed, net of meals eaten. */
    public static final String HUNGER = "hunger";
    /** Character stat: nights in a row without a bed. */
    public static final String FATIGUE = "fatigue";
    /** Character tag: "" (well), {@link #WEAK} or {@link #ILL}. */
    public static final String CONDITION = "condition";
    public static final String WEAK = "weak";
    public static final String ILL = "ill";
    /** Character stat: id of the event that set the current condition. */
    public static final String CONDITION_EVENT = "condition.event";
    /** Settlement stat: beds under a roof, counted by the Minecraft layer. */
    public static final String BEDS = "beds";
    /** Settlement stat: part of a meal already taken from the granary and not yet eaten. */
    public static final String FOOD_CREDIT = "food.credit";
    /** Settlement tag: id of the {@link #FOOD_RAN_OUT} event while the granary is empty, else "". */
    public static final String FOOD_OUT = "food.out";
    /** Settlement tag: id of the last event that took food by force, read as a cause of hunger. */
    public static final String LAST_THEFT = "food.last_theft";

    @Override
    public String id() { return ID; }

    @Override
    public void hourly(Simulation sim) {
        int hourOfDay = Calendar.hourOfDay(sim.state().hour());
        boolean meal = isMealHour(hourOfDay);
        boolean sleep = hourOfDay == Balance.SLEEP_HOUR;
        if (!meal && !sleep) return;
        for (Settlement s : List.copyOf(sim.state().settlements())) {
            if (sim.state().residents(s.id()).isEmpty()) continue;
            if (meal) serveMeal(sim, s);
            if (sleep) sleep(sim, s);
        }
    }

    public static boolean isMealHour(int hourOfDay) {
        int spacing = Balance.HOURS_PER_DAY / Balance.MEALS_PER_PERSON_PER_DAY;
        int offset = hourOfDay - Balance.MEAL_FIRST_HOUR;
        return offset >= 0 && offset % spacing == 0 && offset / spacing < Balance.MEALS_PER_PERSON_PER_DAY;
    }

    /** Meal units one person eats at one meal in this season. */
    public static double mealNeed(Calendar.Season season) {
        return season == Calendar.Season.WINTER ? Balance.WINTER_FOOD_MULTIPLIER : 1.0;
    }

    /** Meal units the whole settlement eats in one day at its current size. */
    public static double dailyNeed(WorldState state, String settlementId, Calendar.Season season) {
        return state.residents(settlementId).size() * Balance.MEALS_PER_PERSON_PER_DAY * mealNeed(season);
    }

    /** Share of normal work a person does in their current condition. */
    public static double outputMultiplier(WorldState state, String characterId) {
        return switch (state.tag(Ref.character(characterId), CONDITION)) {
            case WEAK -> Balance.WEAK_OUTPUT;
            case ILL -> Balance.ILL_OUTPUT;
            default -> 1.0;
        };
    }

    /** The Minecraft layer reports how many beds stand under a roof in the settlement. */
    public static Event countBeds(Simulation sim, String settlementId, int beds, long cause) {
        Settlement s = sim.state().settlement(settlementId);
        if (s == null || sim.state().stat(Ref.settlement(settlementId), BEDS) == beds) return null;
        return sim.emit(EventDraft.of(BEDS_COUNTED).at(s.centre()).actor(Ref.settlement(settlementId)).cause(cause)
                .effect(Effects.setStat(Ref.settlement(settlementId), BEDS, beds))
                .summary(s.name() + " now has " + beds + (beds == 1 ? " bed" : " beds") + " under a roof."));
    }

    private static long dayCause(Simulation sim) {
        CalendarSystem cal = (CalendarSystem) sim.system(CalendarSystem.ID);
        Event day = cal == null ? null : cal.dayEvent(sim);
        return day == null ? 0 : day.id();
    }

    private void serveMeal(Simulation sim, Settlement s) {
        WorldState state = sim.state();
        Ref sref = Ref.settlement(s.id());
        double need = mealNeed(Calendar.season(state.hour()));
        Stockpile stock = state.stockpile(s.stockpileId());
        Map<String, Integer> taken = new LinkedHashMap<>();
        double credit = state.stat(sref, FOOD_CREDIT);
        List<Character> fed = new ArrayList<>();
        List<Character> hungry = new ArrayList<>();
        for (Character c : state.residents(s.id())) {
            while (credit + 1e-9 < need) {
                String best = null;
                for (String item : Food.MEAL_VALUES.keySet()) {
                    if (stock.count(item) - taken.getOrDefault(item, 0) > 0) { best = item; break; }
                }
                if (best == null) break;
                taken.merge(best, 1, Integer::sum);
                credit += Food.MEAL_VALUES.get(best);
            }
            if (credit + 1e-9 >= need) { credit -= need; fed.add(c); } else hungry.add(c);
        }
        long day = dayCause(sim);
        if (!fed.isEmpty() || !taken.isEmpty()) {
            EventDraft d = EventDraft.of(MEAL_SERVED).at(s.centre()).actor(sref).cause(day)
                    .effect(Effects.setStat(sref, FOOD_CREDIT, Math.max(0, credit)));
            taken.forEach((item, n) -> d.effect(Effects.addStock(s.stockpileId(), item, -n)));
            for (Character c : fed) {
                d.actor(Ref.character(c.id()));
                double hunger = state.stat(Ref.character(c.id()), HUNGER);
                if (hunger > 0) d.effect(Effects.setStat(Ref.character(c.id()), HUNGER,
                        Math.max(0, hunger - Balance.MEAL_HUNGER_RECOVERY)));
            }
            if (hungry.isEmpty() && !state.tag(sref, FOOD_OUT).isEmpty()) d.effect(Effects.setTag(sref, FOOD_OUT, ""));
            d.summary(fed.size() + " ate at " + s.name() + (taken.isEmpty() ? "." : ", using " + describe(taken) + "."));
            Event served = sim.emit(d);
            for (Character c : fed) updateCondition(sim, c.id(), served.id());
        }
        if (hungry.isEmpty()) return;
        long outEvent = parse(state.tag(sref, FOOD_OUT));
        if (outEvent == 0) {
            EventDraft out = EventDraft.of(FOOD_RAN_OUT).at(s.centre()).actor(sref).cause(day)
                    .cause(parse(state.tag(sref, LAST_THEFT)))
                    .summary("The granary at " + s.name() + " ran out of food.");
            CalendarSystem cal = (CalendarSystem) sim.system(CalendarSystem.ID);
            if (cal != null && Calendar.season(state.hour()) == Calendar.Season.WINTER) out.causedBy(cal.seasonEvent(sim));
            long id = sim.ledger().size() + 1L;
            out.effect(Effects.setTag(sref, FOOD_OUT, String.valueOf(id)));
            outEvent = sim.emit(out).id();
        }
        EventDraft h = EventDraft.of(WENT_HUNGRY).at(s.centre()).actor(sref).cause(outEvent);
        List<String> names = new ArrayList<>();
        for (Character c : hungry) {
            h.actor(Ref.character(c.id()))
                    .effect(Effects.addStat(Ref.character(c.id()), HUNGER, 1));
            names.add(c.name());
        }
        Event went = sim.emit(h.summary(String.join(", ", names) + " went without a meal at " + s.name() + "."));
        for (Character c : hungry) updateCondition(sim, c.id(), went.id());
    }

    private void sleep(Simulation sim, Settlement s) {
        WorldState state = sim.state();
        Ref sref = Ref.settlement(s.id());
        int beds = (int) state.stat(sref, BEDS);
        List<Character> people = state.residents(s.id());
        List<Character> rested = people.subList(0, Math.min(beds, people.size()));
        List<Character> rough = people.subList(Math.min(beds, people.size()), people.size());
        long day = dayCause(sim);
        if (!rested.isEmpty()) {
            EventDraft d = EventDraft.of(SLEPT).at(s.centre()).actor(sref).cause(day)
                    .summary(rested.size() + " slept in a bed at " + s.name() + ".");
            boolean anyChange = false;
            for (Character c : rested) {
                d.actor(Ref.character(c.id()));
                if (state.stat(Ref.character(c.id()), FATIGUE) > 0) {
                    d.effect(Effects.setStat(Ref.character(c.id()), FATIGUE, 0));
                    anyChange = true;
                }
            }
            if (anyChange) {
                Event e = sim.emit(d);
                for (Character c : rested) updateCondition(sim, c.id(), e.id());
            }
        }
        if (rough.isEmpty()) return;
        EventDraft d = EventDraft.of(SLEPT_ROUGH).at(s.centre()).actor(sref).cause(day);
        Event bedsEvent = sim.ledger().latest(BEDS_COUNTED);
        if (bedsEvent != null && bedsEvent.actors().contains(sref)) d.causedBy(bedsEvent);
        List<String> names = new ArrayList<>();
        for (Character c : rough) {
            d.actor(Ref.character(c.id())).effect(Effects.addStat(Ref.character(c.id()), FATIGUE, 1));
            names.add(c.name());
        }
        Event e = sim.emit(d.summary(String.join(", ", names) + " slept on the ground at " + s.name()
                + " for want of a bed."));
        for (Character c : rough) updateCondition(sim, c.id(), e.id());
    }

    /**
     * Moves a person one stage towards what their hunger and fatigue call for. Getting worse is
     * one stage per call; getting better goes straight to the stage they have earned.
     */
    static void updateCondition(Simulation sim, String characterId, long cause) {
        WorldState state = sim.state();
        Character c = state.character(characterId);
        if (c == null || !c.alive()) return;
        Ref ref = Ref.character(characterId);
        double hunger = state.stat(ref, HUNGER), fatigue = state.stat(ref, FATIGUE);
        int target = hunger >= Balance.HUNGER_FATAL || fatigue >= Balance.FATIGUE_FATAL ? 3
                : hunger >= Balance.HUNGER_ILL || fatigue >= Balance.FATIGUE_ILL ? 2
                : hunger >= Balance.HUNGER_WEAK || fatigue >= Balance.FATIGUE_WEAK ? 1 : 0;
        String tag = state.tag(ref, CONDITION);
        int now = tag.equals(ILL) ? 2 : tag.equals(WEAK) ? 1 : 0;
        if (target == now) return;
        long previous = (long) state.stat(ref, CONDITION_EVENT);
        String why = hunger >= fatigue * ((double) Balance.HUNGER_FATAL / Balance.FATIGUE_FATAL) ? "hunger" : "exhaustion";
        long nextId = sim.ledger().size() + 1L;
        if (target < now) {
            String newTag = target == 1 ? WEAK : "";
            sim.emit(EventDraft.of(RECOVERED).actor(ref).cause(cause).cause(previous)
                    .effect(Effects.setTag(ref, CONDITION, newTag))
                    .effect(Effects.setStat(ref, CONDITION_EVENT, target == 0 ? 0 : nextId))
                    .summary(c.name() + (target == 0 ? " is well again." : " is recovering but still weak.")));
            return;
        }
        if (now == 0) {
            sim.emit(EventDraft.of(WEAKENED).actor(ref).cause(cause).cause(previous)
                    .effect(Effects.setTag(ref, CONDITION, WEAK))
                    .effect(Effects.setStat(ref, CONDITION_EVENT, nextId))
                    .summary(c.name() + " is weak from " + why + " and working slowly."));
        } else if (now == 1) {
            sim.emit(EventDraft.of(FELL_ILL).actor(ref).cause(cause).cause(previous)
                    .effect(Effects.setTag(ref, CONDITION, ILL))
                    .effect(Effects.setStat(ref, CONDITION_EVENT, nextId))
                    .summary(c.name() + " fell ill from " + why + "."));
        } else if (SurvivorTables.trait(c.trait()).leavesWhenDesperate()) {
            People.leave(sim, characterId, c.name() + ", " + c.trait() + " and sick of " + why
                    + ", gave up on the camp and left.", cause, previous);
        } else {
            People.die(sim, characterId, c.name() + " died of " + why + ".", List.of(), cause, previous);
        }
    }

    private static long parse(String s) {
        return s.isEmpty() ? 0 : Long.parseLong(s);
    }

    private static String describe(Map<String, Integer> items) {
        List<ItemCount> list = new ArrayList<>();
        items.forEach((k, v) -> list.add(new ItemCount(k, v)));
        return ItemCount.describe(list);
    }
}
