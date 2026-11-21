package io.github.bl4z3es.riseofempires.core.ledger;

import java.util.Objects;

/** A block position in a dimension, as plain values. */
public record Place(String dimension, int x, int y, int z) {
	public Place {
		Objects.requireNonNull(dimension, "dimension");
	}

	/** Squared horizontal distance to another place, ignoring y. Different dimensions are infinitely far. */
	public long horizontalDistanceSq(Place other) {
		if (!dimension.equals(other.dimension)) return Long.MAX_VALUE;
		long dx = x - other.x, dz = z - other.z;
		return dx * dx + dz * dz;
	}

	@Override
	public String toString() { return dimension + " " + x + " " + y + " " + z; }
}
