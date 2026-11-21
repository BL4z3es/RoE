package io.github.bl4z3es.riseofempires.core.model;

import io.github.bl4z3es.riseofempires.core.ledger.Place;

/**
 * A barbarian raid on a settlement.
 *
 * @param origin      where the raiders gather, beyond sight of the camp
 * @param sightTime   world time when the warning is given
 * @param arriveTime  world time when the raiders reach the camp
 * @param raidersLeft raiders still fighting
 */
public record Raid(long id, long settlementId, int size, long plannedEventId, Place origin,
		long sightTime, long arriveTime, RaidStage stage, int raidersLeft) {
	public Raid withStage(RaidStage s) { return new Raid(id, settlementId, size, plannedEventId, origin, sightTime, arriveTime, s, raidersLeft); }
	public Raid withRaidersLeft(int n) { return new Raid(id, settlementId, size, plannedEventId, origin, sightTime, arriveTime, stage, n); }
}
