package io.github.bl4z3es.riseofempires.core.sim.camp;

import java.util.List;

/**
 * The pools survivors are drawn from. Hollowcrown flavour: a heartland that fell into civil war
 * when the last emperor died. All text is PROPOSED placeholder writing.
 */
public final class SurvivorTables {
    private SurvivorTables() {}

    public static final List<String> NAMES = List.of(
            "Mara", "Tobin", "Elsbeth", "Corrin", "Wenna", "Aldous", "Brannoc", "Isolde",
            "Hesketh", "Rowan", "Gilda", "Osric", "Maud", "Fenwick", "Agnes", "Teodric",
            "Ysolde", "Bartram", "Linnet", "Edric", "Sabine", "Hob", "Clemence", "Wystan",
            "Joan", "Piers", "Ottilie", "Garrick");

    /** A trait shapes how a person speaks and what they do when things go badly. */
    public record Trait(String name, boolean leavesWhenDesperate, String voice) {}

    public static final List<Trait> TRAITS = List.of(
            new Trait("stubborn", false, "Mark me:"),
            new Trait("loyal", false, "If I may,"),
            new Trait("restless", true, "We can't sit still."),
            new Trait("proud", true, "I won't beg, but"),
            new Trait("gentle", false, "Forgive me for saying it, but"),
            new Trait("wary", false, "I don't like it."),
            new Trait("cheerful", false, "Chin up, but"),
            new Trait("grim", false, "No sense hiding it:"),
            new Trait("devout", false, "Saints keep us,"),
            new Trait("greedy", true, "If you want my labour,"));

    public static final List<String> WANTS = List.of(
            "a roof of their own",
            "to see the Crown Seat restored",
            "to never go hungry again",
            "to find a brother lost in the fighting",
            "land to farm and keep",
            "revenge on the men who burned their village",
            "to be counted among the free",
            "a quiet winter by a fire",
            "to learn to read",
            "to be remembered for something");

    public static final List<String> MEMORIES = List.of(
            "Saw the granary at Eastmere burn with the harvest inside.",
            "Hid in a ditch while Frostmere banners rode past.",
            "Buried their mother under the old empire's milestone.",
            "Carried water for the regent's army and was never paid.",
            "Watched the last market at Hollow Ford break up in a riot.",
            "Lost two fingers to frost in the hard winter three years back.",
            "Found a scholar's book in a ruin and kept it, unread.",
            "Fled when raiders came down the river at night.",
            "Heard the bells ring for the emperor's death.",
            "Shared their last bread with a stranger on the road.");

    public static Trait trait(String name) {
        for (Trait t : TRAITS) if (t.name().equals(name)) return t;
        return TRAITS.get(0);
    }
}
