package io.github.bl4z3es.riseofempires.core.ledger;

/** Where something happened. {@link #NOWHERE} is used for events with no location. */
public record Place(String dimension, int x, int y, int z) {
    public static final String OVERWORLD = "minecraft:overworld";
    public static final Place NOWHERE = new Place("", 0, 0, 0);

    public static Place overworld(int x, int y, int z) {
        return new Place(OVERWORLD, x, y, z);
    }

    public boolean isNowhere() {
        return dimension.isEmpty();
    }

    /** Horizontal distance in blocks. Infinite if the dimensions differ. */
    public double distanceTo(Place other) {
        if (!dimension.equals(other.dimension)) return Double.POSITIVE_INFINITY;
        double dx = x - other.x, dz = z - other.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public String encode() {
        return isNowhere() ? "" : dimension + "@" + x + "," + y + "," + z;
    }

    public static Place decode(String s) {
        if (s.isEmpty()) return NOWHERE;
        int at = s.lastIndexOf('@');
        String[] c = s.substring(at + 1).split(",");
        return new Place(s.substring(0, at), Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2]));
    }
}
