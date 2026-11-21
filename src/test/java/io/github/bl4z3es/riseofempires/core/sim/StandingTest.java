package io.github.bl4z3es.riseofempires.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.bl4z3es.riseofempires.core.model.SimState;
import org.junit.jupiter.api.Test;

class StandingTest {
	@Test
	void threeResidentsRadius32() {
		SimState s = new SimState(1);
		long sid = Fixtures.camp(s, 0, 3);
		Standing.Parts p = Standing.of(s, sid);
		assertEquals(3, p.population());
		assertEquals(25, p.territoryChunks());
		assertEquals(0.30 * Math.log(3) / Math.log(2), p.populationTerm(), 1e-9);
		assertEquals(0.25 * Math.log(25) / Math.log(2), p.territoryTerm(), 1e-9);
		assertEquals(p.populationTerm() + p.territoryTerm(), p.total(), 1e-9);
	}

	@Test
	void deadResidentsDoNotCount() {
		SimState s = new SimState(1);
		long sid = Fixtures.camp(s, 0, 2);
		Fixtures.kill(s, s.settlement(sid).orElseThrow().population().get(0), 10);
		assertEquals(1, Standing.of(s, sid).population());
		assertEquals(0.0, Standing.of(s, sid).populationTerm(), 1e-9);
	}
}
