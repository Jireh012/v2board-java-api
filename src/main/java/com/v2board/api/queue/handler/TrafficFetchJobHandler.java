package com.v2board.api.queue.handler;

import com.v2board.api.queue.JobHandler;
import com.v2board.api.queue.JobPayload;
import com.v2board.api.queue.JobQueues;
import com.v2board.api.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class TrafficFetchJobHandler implements JobHandler {

    @Autowired
    private UserService userService;

    @Override
    public String type() {
        return JobQueues.TYPE_TRAFFIC_FETCH;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handle(JobPayload payload) {
        double rate = toDouble(payload.getData().get("rate"));
        Object raw = payload.getData().get("data");
        Map<String, List<Long>> data = coerceTrafficMap(raw);
        userService.trafficFetch(rate, data);
    }

    public static Map<String, List<Long>> coerceTrafficMap(Object raw) {
        Map<String, List<Long>> out = new HashMap<>();
        if (!(raw instanceof Map<?, ?> m)) {
            return out;
        }
        for (Map.Entry<?, ?> e : m.entrySet()) {
            String key = String.valueOf(e.getKey());
            List<Long> vals = new ArrayList<>();
            if (e.getValue() instanceof List<?> list) {
                for (Object o : list) {
                    vals.add(toLong(o));
                }
            }
            out.put(key, vals);
        }
        return out;
    }

    private static double toDouble(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        if (o == null) {
            return 1.0;
        }
        return Double.parseDouble(String.valueOf(o));
    }

    private static long toLong(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        if (o == null) {
            return 0L;
        }
        return Long.parseLong(String.valueOf(o));
    }
}
