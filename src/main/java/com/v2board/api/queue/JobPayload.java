package com.v2board.api.queue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class JobPayload {

    private String id;
    private String type;
    private String queue;
    private int attempts;
    private int maxAttempts = 3;
    private long createdAt;
    private Map<String, Object> data = new LinkedHashMap<>();

    public static JobPayload create(String queue, String type, Map<String, Object> data) {
        JobPayload p = new JobPayload();
        p.id = UUID.randomUUID().toString();
        p.queue = queue;
        p.type = type;
        p.attempts = 0;
        p.maxAttempts = 3;
        p.createdAt = System.currentTimeMillis() / 1000;
        if (data != null) {
            p.data.putAll(data);
        }
        return p;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getQueue() {
        return queue;
    }

    public void setQueue(String queue) {
        this.queue = queue;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data != null ? data : new LinkedHashMap<>();
    }
}
