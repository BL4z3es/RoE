package io.github.bl4z3es.riseofempires.core.sim.camp;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Professions;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.sim.Calendar;
import io.github.bl4z3es.riseofempires.core.sim.Simulation;
import io.github.bl4z3es.riseofempires.core.sim.WorldState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What the survivors tell the player the camp needs (task M1-22). Template text only, spoken by
 * a resident in the voice of their trait. No pop-ups: the Minecraft layer has people say these.
 */
public final class Guidance {
    private Guidance() {}

    public static final int PRIORITY_RAID = 100;
    public static final int PRIORITY_ILL = 90;
    public static final int PRIORITY_HUNGRY = 85;
    public static final int PRIORITY_WINTER = 70;
    public static final int PRIORITY_BEDS = 60;
    public static final int PRIORITY_FARMER = 50;
    public static final int PRIORITY_SEEDS = 45;
    public static final int PRIORITY_IDLE = 40;
    public static final int PRIORITY_BUILD = 30;
    /** Days of food below which the granary counts as low. */
    public static final int LOW_FOOD_DAYS = 2;
    /** Days before winter from which the stores are checked against the winter's need. */
    public static final int WINTER_LOOKAHEAD_DAYS = Balance.SEASON_LENGTH_DAYS * 2;

    /** Advice for this settlement right now, most urgent first. Empty if nobody lives there. */
    public static List<Advice> advise(Simulation sim, String settlementId) {
        WorldState state = sim.state();
        Settlement s = state.settlement(settlementId);
        List<Advice> out = new ArrayList<>();
        if (s == null) return out;
        List<Character> people = state.residents(settlementId);
        if (people.isEmpty()) return out;
        long hour = state.hour();
        Calendar.Season season = Calendar.season(hour);

        String raidId = Raids.raidOn(state, settlementId);
        String raidState = raidId.isEmpty() ? "" : Raids.state(state, raidId);
        if (raidState.equals(Raids.STATE_ANNOUNCED) || raidState.equals(Raids.STATE_ARRIVED)) {
            Ref r = Raids.ref(raidId);
            long arrive = (long) state.stat(r, Raids.ARRIVE_HOUR);
            long hours = Math.max(0, arrive - hour);
            Character who = pick(people, Professions.FIGHTER);
            out.add(say(who, PRIORITY_RAID, (long) state.stat(r, Raids.LAST_EVENT), "raid",
                    raidState.equals(Raids.STATE_ARRIVED)
                            ? "they're here, at the granary! Everyone who can hold a blade, now!"
                            : "raiders from the " + state.tag(r, Raids.DIRECTION) + ", about " + (int) state.stat(r, Raids.SIZE)
                            + " of them, here in " + hours + " hours. They want the food. Arm whoever you can."));
        }
        List<Character> ill = people.stream()
                .filter(c -> state.tag(Ref.character(c.id()), Needs.CONDITION).equals(Needs.ILL)).toList();
        if (!ill.isEmpty()) {
            Character first = ill.get(0);
            Character who = ill.size() == people.size() ? first : other(people, first);
            String names = ill.size() == people.size() ? "All of us are" : ill.size() == 1 ? first.name() + " is"
                    : String.join(" and ", ill.stream().map(Character::name).toList()) + " are";
            out.add(say(who, PRIORITY_ILL, (long) state.stat(Ref.character(first.id()), Needs.CONDITION_EVENT), "ill",
                    names + " sick. Food and a bed under a roof, or there'll be graves to dig."));
        }
        double meals = Food.meals(state.stockpile(s.stockpileId()));
        double daily = Needs.dailyNeed(state, settlementId, season);
        if (meals < daily * LOW_FOOD_DAYS) {
            Event out_ = sim.ledger().get(parse(state.tag(Ref.settlement(settlementId), Needs.FOOD_OUT)));
            out.add(say(pick(people, Professions.FARMER), PRIORITY_HUNGRY, out_ == null ? 0 : out_.id(), "food",
                    meals < 1 ? "the granary is empty. We'll go hungry tonight."
                            : "there's barely " + (int) Math.floor(meals / Math.max(1, daily) * Balance.MEALS_PER_PERSON_PER_DAY)
                            + " meals left in the granary. We need bread, meat, anything."));
        }
        if (season != Calendar.Season.WINTER) {
            int daysToWinter = daysUntilWinter(hour);
            double winterNeed = Needs.dailyNeed(state, settlementId, Calendar.Season.WINTER) * Balance.SEASON_LENGTH_DAYS;
            if (daysToWinter <= WINTER_LOOKAHEAD_DAYS && meals < winterNeed) {
                out.add(say(people.get(0), PRIORITY_WINTER, parse(state.tag(Ref.settlement(settlementId), ActOne.WARNED)),
                        "winter", "winter is " + daysToWinter + " days off. We need " + (int) Math.ceil(winterNeed)
                                + " meals put by and we have " + (int) Math.floor(meals) + "."));
            }
        }
        int beds = (int) state.stat(Ref.settlement(settlementId), Needs.BEDS);
        if (beds < people.size()) {
            Character tired = people.get(Math.min(beds, people.size() - 1));
            out.add(say(tired, PRIORITY_BEDS, 0, "beds", "there are " + people.size() + " of us and "
                    + (beds == 0 ? "no beds" : beds == 1 ? "one bed" : beds + " beds")
                    + ". Sleeping on the ground wears a body down. A bunkhouse would do it."));
        }
        boolean farmer = people.stream().anyMatch(c -> c.profession().equals(Professions.FARMER));
        if (!farmer && season != Calendar.Season.WINTER) {
            out.add(say(people.get(0), PRIORITY_FARMER, 0, "farmer",
                    "nobody's working the land. Make one of us a farmer, or we'll eat the seed corn by winter."));
        }
        if (farmer && state.stockpile(s.stockpileId()).count(Food.SEEDS) == 0) {
            boolean planted = people.stream().anyMatch(c -> state.stat(Ref.character(c.id()), Jobs.CROPS) > 0);
            if (!planted) out.add(say(pick(people, Professions.FARMER), PRIORITY_SEEDS, 0, "seeds",
                    "there's no seed left to plant. Break some grass for wheat seeds and put them in the granary."));
        }
        for (Character c : people) {
            if (c.profession().isEmpty()) {
                out.add(say(c, PRIORITY_IDLE, 0, "idle", "I've nothing to do. Give me work: farming, gathering, building."));
                break;
            }
        }
        for (Character c : people) {
            String reason = state.tag(Ref.character(c.id()), Jobs.IDLE_REASON);
            if (c.profession().equals(Professions.BUILDER) && !reason.isEmpty()) {
                out.add(say(c, PRIORITY_BUILD, 0, "build", reason + "."));
                break;
            }
        }
        out.sort(Comparator.comparingInt(Advice::priority).reversed());
        return out;
    }

    private static int daysUntilWinter(long hour) {
        int dayOfYear = Calendar.dayOfYear(hour);
        int winterStart = Balance.SEASON_LENGTH_DAYS * Calendar.Season.WINTER.ordinal();
        return winterStart - dayOfYear;
    }

    private static Advice say(Character speaker, int priority, long basedOn, String topic, String line) {
        String voice = SurvivorTables.trait(speaker.trait()).voice();
        return new Advice(speaker.id(), priority, basedOn, topic, voice + " " + line);
    }

    private static Character pick(List<Character> people, String profession) {
        for (Character c : people) if (c.profession().equals(profession)) return c;
        return people.get(0);
    }

    private static Character other(List<Character> people, Character not) {
        for (Character c : people) if (!c.id().equals(not.id())) return c;
        return not;
    }

    private static long parse(String s) {
        return s.isEmpty() ? 0 : Long.parseLong(s);
    }
}
