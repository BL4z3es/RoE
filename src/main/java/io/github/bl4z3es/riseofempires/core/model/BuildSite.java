package io.github.bl4z3es.riseofempires.core.model;

import io.github.bl4z3es.riseofempires.core.ledger.Place;

/**
 * A blueprint placed in the world and being built block by block.
 *
 * @param rotation quarter turns clockwise, 0 to 3
 * @param placed   how many of the blueprint's blocks are built, in build order
 * @param total    how many blocks the blueprint has
 */
public record BuildSite(long id, long settlementId, String blueprintId, Place origin, int rotation,
		int placed, int total, long placedEventId) {
	public boolean complete() { return placed >= total; }
	public BuildSite withPlaced(int n) { return new BuildSite(id, settlementId, blueprintId, origin, rotation, n, total, placedEventId); }
}
