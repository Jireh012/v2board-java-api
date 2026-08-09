package com.v2board.api.queue.handler;

import com.v2board.api.queue.JobHandler;
import com.v2board.api.queue.JobPayload;
import com.v2board.api.queue.JobQueues;
import com.v2board.api.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class StatServerJobHandler implements JobHandler {

    @Autowired
    private UserService userService;

    @Override
    public String type() {
        return JobQueues.TYPE_STAT_SERVER;
    }

    @Override
    public void handle(JobPayload payload) {
        double rate = payload.getData().get("rate") instanceof Number n ? n.doubleValue() : 1.0;
        long serverId = payload.getData().get("server_id") instanceof Number n ? n.longValue() : 0L;
        String serverType = String.valueOf(payload.getData().getOrDefault("server_type", ""));
        userService.recordStatServer(
                TrafficFetchJobHandler.coerceTrafficMap(payload.getData().get("data")),
                serverId,
                serverType,
                rate);
    }
}
