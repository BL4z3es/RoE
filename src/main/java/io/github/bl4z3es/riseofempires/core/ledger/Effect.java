package io.github.bl4z3es.riseofempires.core.ledger;

import java.util.Objects;

/**
 * One state change an event applied, written for a person to read.
 * Example: target {@code settlement:1}, what {@code food}, detail {@code -6}.
 */
public record Effect(Ref target, String what, String detail) {
	public Effect {
		Objects.requireNonNull(target, "target");
		Objects.requireNonNull(what, "what");
		Objects.requireNonNull(detail, "detail");
	}

	@Override
	public String toString() { return target + " " + what + " " + detail; }
}
