package io.github.bl4z3es.riseofempires.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A person. The record exists whether or not a body is loaded in the world (CONTEXT.md 3.3).
 *
 * @param settlementId the settlement they live in, or 0 for none
 */
public record Character(
		long id,
		String name,
		String trait,
		String want,
		Profession profession,
		List<Memory> memories,
		Needs needs,
		Condition condition,
		long settlementId) {
	public Character {
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(trait, "trait");
		Objects.requireNonNull(want, "want");
		Objects.requireNonNull(profession, "profession");
		Objects.requireNonNull(needs, "needs");
		Objects.requireNonNull(condition, "condition");
		memories = List.copyOf(memories);
	}

	public Character withProfession(Profession p) { return new Character(id, name, trait, want, p, memories, needs, condition, settlementId); }
	public Character withNeeds(Needs n) { return new Character(id, name, trait, want, profession, memories, n, condition, settlementId); }
	public Character withCondition(Condition c) { return new Character(id, name, trait, want, profession, memories, needs, c, settlementId); }
	public Character withSettlement(long s) { return new Character(id, name, trait, want, profession, memories, needs, condition, s); }

	public Character remembering(Memory m) {
		List<Memory> next = new ArrayList<>(memories);
		next.add(m);
		return new Character(id, name, trait, want, profession, next, needs, condition, settlementId);
	}
}
