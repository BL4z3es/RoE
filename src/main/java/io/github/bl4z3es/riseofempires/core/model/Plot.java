package io.github.bl4z3es.riseofempires.core.model;

import io.github.bl4z3es.riseofempires.core.ledger.Place;

/** One farmland crop a settlement tends. {@code age} uses the crop's own growth stages, 0 to 7 for wheat. */
public record Plot(Place pos, String crop, int age) {
	public Plot withAge(int a) { return new Plot(pos, crop, a); }
}
