package com.v2board.api.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JobPayloadTest {

    @Test
    void roundTripJson() throws Exception {
        JobPayload p = JobPayload.create(JobQueues.ORDER_HANDLE, JobQueues.TYPE_ORDER_HANDLE,
                Map.of("trade_no", "T1"));
        ObjectMapper om = new ObjectMapper();
        String json = om.writeValueAsString(p);
        JobPayload back = om.readValue(json, JobPayload.class);
        assertEquals(JobQueues.TYPE_ORDER_HANDLE, back.getType());
        assertEquals("T1", back.getData().get("trade_no"));
        assertNotNull(back.getId());
    }
}
