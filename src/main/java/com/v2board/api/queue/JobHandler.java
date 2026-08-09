package com.v2board.api.queue;

public interface JobHandler {
    String type();

    void handle(JobPayload payload) throws Exception;
}
