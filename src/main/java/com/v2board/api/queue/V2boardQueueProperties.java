package com.v2board.api.queue;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "v2board.queue")
public class V2boardQueueProperties {

    private boolean enabled = true;
    private int reclaimSeconds = 300;
    private int brpopTimeoutSeconds = 2;
    private Map<String, QueueConfig> queues = defaultQueues();

    private static Map<String, QueueConfig> defaultQueues() {
        Map<String, QueueConfig> m = new LinkedHashMap<>();
        m.put(JobQueues.ORDER_HANDLE, new QueueConfig(2));
        m.put(JobQueues.TRAFFIC_FETCH, new QueueConfig(2));
        m.put(JobQueues.STAT, new QueueConfig(2));
        m.put(JobQueues.SEND_EMAIL, new QueueConfig(1));
        m.put(JobQueues.SEND_TELEGRAM, new QueueConfig(1));
        return m;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getReclaimSeconds() {
        return reclaimSeconds;
    }

    public void setReclaimSeconds(int reclaimSeconds) {
        this.reclaimSeconds = reclaimSeconds;
    }

    public int getBrpopTimeoutSeconds() {
        return brpopTimeoutSeconds;
    }

    public void setBrpopTimeoutSeconds(int brpopTimeoutSeconds) {
        this.brpopTimeoutSeconds = brpopTimeoutSeconds;
    }

    public Map<String, QueueConfig> getQueues() {
        return queues;
    }

    public void setQueues(Map<String, QueueConfig> queues) {
        this.queues = queues != null ? queues : defaultQueues();
    }

    public static class QueueConfig {
        private int concurrency = 1;

        public QueueConfig() {
        }

        public QueueConfig(int concurrency) {
            this.concurrency = concurrency;
        }

        public int getConcurrency() {
            return concurrency;
        }

        public void setConcurrency(int concurrency) {
            this.concurrency = Math.max(1, concurrency);
        }
    }
}
