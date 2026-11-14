package io.github.bl4z3es.riseofempires.core.ledger;

/**
 * A pointer to anything an event can involve. {@code kind} is one of the constants below or a
 * kind a later system adds; {@code id} is unique within its kind.
 */
public record Ref(String kind, String id) {
    public static final String CHARACTER = "character";
    public static final String SETTLEMENT = "settlement";
    public static final String NATION = "nation";
    public static final String STOCKPILE = "stockpile";
    public static final String PLAYER = "player";
    public static final String WORLD = "world";

    public static final Ref THE_WORLD = new Ref(WORLD, "world");

    public static Ref character(String id) { return new Ref(CHARACTER, id); }
    public static Ref settlement(String id) { return new Ref(SETTLEMENT, id); }
    public static Ref nation(String id) { return new Ref(NATION, id); }
    public static Ref stockpile(String id) { return new Ref(STOCKPILE, id); }
    public static Ref player(String id) { return new Ref(PLAYER, id); }

    public static Ref parse(String s) {
        int i = s.indexOf(':');
        if (i < 0) throw new IllegalArgumentException("Not a ref: " + s);
        return new Ref(s.substring(0, i), s.substring(i + 1));
    }

    @Override
    public String toString() {
        return kind + ":" + id;
    }
}
