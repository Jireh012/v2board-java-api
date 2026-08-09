package com.v2board.api.queue.handler;

import com.v2board.api.queue.JobHandler;
import com.v2board.api.queue.JobPayload;
import com.v2board.api.queue.JobQueues;
import com.v2board.api.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class StatUserJobHandler implements JobHandler {

    @Autowired
    private UserService userService;

    @Override
    public String type() {
        return JobQueues.TYPE_STAT_USER;
    }

    @Override
    public void handle(JobPayload payload) throws Exception {
        double rate = payload.getData().get("rate") instanceof Number n ? n.doubleValue() : 1.0;
        userService.recordStatUser(TrafficFetchJobHandler.coerceTrafficMap(payload.getData().get("data")), rate);
    }
}
