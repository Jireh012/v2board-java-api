package com.v2board.api.queue;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "v2board.queue", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JobReclaimSchedule {

    @Autowired
    private RedisJobQueue jobQueue;

    @Scheduled(fixedDelayString = "${v2board.queue.reclaim-scan-ms:30000}")
    public void reclaim() {
        jobQueue.reclaimStale();
    }
}
