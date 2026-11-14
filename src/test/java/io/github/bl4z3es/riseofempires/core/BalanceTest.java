package io.github.bl4z3es.riseofempires.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BalanceTest {
    @Test
    void yearIsFourSeasons() {
        assertEquals(Balance.YEAR_LENGTH_DAYS, 4 * Balance.SEASON_LENGTH_DAYS);
    }
}
