package com.v2board.api.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerServiceAvailableStatusTest {

    @Test
    void computeAvailableStatus_matchesPhpThreeState() {
        long now = 1_700_000_000L;
        assertEquals(0, ServerService.computeAvailableStatus(now, 0, 0));
        assertEquals(0, ServerService.computeAvailableStatus(now, now - 301, now));
        assertEquals(1, ServerService.computeAvailableStatus(now, now - 10, now - 301));
        assertEquals(2, ServerService.computeAvailableStatus(now, now - 10, now - 10));
        assertEquals(2, ServerService.computeAvailableStatus(now, now, now));
    }
}
