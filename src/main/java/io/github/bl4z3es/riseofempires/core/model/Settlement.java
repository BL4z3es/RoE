package io.github.bl4z3es.riseofempires.core.model;

import io.github.bl4z3es.riseofempires.core.ledger.Place;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A settlement and its claim (CONTEXT.md 3.2).
 *
 * @param founder    the founding player's UUID as text
 * @param population ids of the characters who live here, in the order they joined
 * @param granary    where its Granary Seal is, or {@code null} if it has none yet
 * @param foundedEventId the {@code settlement_founded} event
 */
public record Settlement(
		long id,
		String name,
		String founder,
		Place centre,
		int claimRadius,
		List<Long> population,
		Place granary,
		long foundedEventId) {
	public Settlement {
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(founder, "founder");
		Objects.requireNonNull(centre, "centre");
		population = List.copyOf(population);
	}

	/** True if the place is inside the claim: same dimension, horizontal distance at most the radius. */
	public boolean claims(Place p) {
		return centre.horizontalDistanceSq(p) <= (long) claimRadius * claimRadius;
	}

	/** Number of chunks the claim touches, used by Standing. */
	public int territoryChunks() {
		int minCx = Math.floorDiv(centre.x() - claimRadius, 16), maxCx = Math.floorDiv(centre.x() + claimRadius, 16);
		int minCz = Math.floorDiv(centre.z() - claimRadius, 16), maxCz = Math.floorDiv(centre.z() + claimRadius, 16);
		return (maxCx - minCx + 1) * (maxCz - minCz + 1);
	}

	public Settlement withResident(long characterId) {
		if (population.contains(characterId)) return this;
		List<Long> next = new ArrayList<>(population);
		next.add(characterId);
		return new Settlement(id, name, founder, centre, claimRadius, next, granary, foundedEventId);
	}

	public Settlement withoutResident(long characterId) {
		List<Long> next = new ArrayList<>(population);
		next.remove(characterId);
		return new Settlement(id, name, founder, centre, claimRadius, next, granary, foundedEventId);
	}

	public Settlement withGranary(Place g) { return new Settlement(id, name, founder, centre, claimRadius, population, g, foundedEventId); }
}
