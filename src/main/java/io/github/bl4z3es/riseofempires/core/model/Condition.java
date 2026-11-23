package io.github.bl4z3es.riseofempires.core.model;

/**
 * How a character is doing. Unmet needs move a character down this list one step at a time.
 * {@link #DEAD} and {@link #LEFT} are final: the record stays, the person is gone.
 */
public enum Condition {
	WELL, STRAINED, WEAK, ILL, DEAD, LEFT;

	public boolean isGone() { return this == DEAD || this == LEFT; }
}
