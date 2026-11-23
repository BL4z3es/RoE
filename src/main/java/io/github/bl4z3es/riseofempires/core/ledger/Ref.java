package io.github.bl4z3es.riseofempires.core.ledger;

import java.util.Objects;

/** A reference to something that takes part in an event: a character, settlement, player or raid. */
public record Ref(String kind, String id) {
	public static final String CHARACTER = "character";
	public static final String SETTLEMENT = "settlement";
	public static final String PLAYER = "player";
	public static final String RAID = "raid";
	public static final String WORLD = "world";

	public Ref {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(id, "id");
	}

	public static Ref character(long id) { return new Ref(CHARACTER, Long.toString(id)); }
	public static Ref settlement(long id) { return new Ref(SETTLEMENT, Long.toString(id)); }
	public static Ref player(String uuid) { return new Ref(PLAYER, uuid); }
	public static Ref raid(long id) { return new Ref(RAID, Long.toString(id)); }
	public static Ref world() { return new Ref(WORLD, "world"); }

	@Override
	public String toString() { return kind + ":" + id; }
}
