package com.v2board.api.queue;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobQueuesTest {

    @Test
    void displayNames_matchPhpLabels() {
        Map<String, String> m = JobQueues.displayNames();
        assertEquals("订单队列", m.get(JobQueues.ORDER_HANDLE));
        assertEquals("邮件队列", m.get(JobQueues.SEND_EMAIL));
        assertEquals("Telegram消息队列", m.get(JobQueues.SEND_TELEGRAM));
        assertEquals("统计队列", m.get(JobQueues.STAT));
        assertEquals("流量消费队列", m.get(JobQueues.TRAFFIC_FETCH));
        assertTrue(m.size() >= 5);
    }
}
