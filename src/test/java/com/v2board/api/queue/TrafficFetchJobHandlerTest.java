package com.v2board.api.queue;

import com.v2board.api.queue.handler.TrafficFetchJobHandler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrafficFetchJobHandlerTest {

    @Test
    void coerceTrafficMap_acceptsNumberLists() {
        Map<String, List<Long>> m = TrafficFetchJobHandler.coerceTrafficMap(
                Map.of("12", List.of(1, 2), "13", List.of(10L, 20L)));
        assertEquals(1L, m.get("12").get(0));
        assertEquals(2L, m.get("12").get(1));
        assertEquals(10L, m.get("13").get(0));
    }
}
