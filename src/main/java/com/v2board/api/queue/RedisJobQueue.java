package com.v2board.api.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.mapper.JobFailedMapper;
import com.v2board.api.model.JobFailed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis List waiting + ZSET processing job broker.
 */
@Component
public class RedisJobQueue {

    private static final Logger logger = LoggerFactory.getLogger(RedisJobQueue.class);
    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private final RedisTemplate<String, String> redis;
    private final ObjectMapper objectMapper;
    private final JobFailedMapper jobFailedMapper;
    private final V2boardQueueProperties props;
    private final AtomicBoolean workersRunning = new AtomicBoolean(false);

    @Autowired
    public RedisJobQueue(
            @Qualifier("queueRedisTemplate") RedisTemplate<String, String> redis,
            ObjectMapper objectMapper,
            JobFailedMapper jobFailedMapper,
            V2boardQueueProperties props) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.jobFailedMapper = jobFailedMapper;
        this.props = props;
    }

    public void setWorkersRunning(boolean running) {
        workersRunning.set(running);
    }

    public boolean isWorkersRunning() {
        return workersRunning.get() && props.isEnabled();
    }

    public String waitingKey(String queue) {
        return "v2board:queue:" + queue;
    }

    public String processingKey(String queue) {
        return "v2board:queue:" + queue + ":processing";
    }

    public String completedKey(String queue) {
        return "v2board:queue:meta:" + queue + ":completed:" + LocalDateTime.now().format(HOUR_FMT);
    }

    public void dispatch(JobPayload payload) {
        if (!props.isEnabled()) {
            throw new IllegalStateException("v2board.queue.enabled=false");
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            redis.opsForList().leftPush(waitingKey(payload.getQueue()), json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("serialize job failed", e);
        }
    }

    /** Block-pop waiting job and mark processing. Null on timeout / empty. */
    public ClaimedJob claimOnce(String queue) {
        String json = redis.opsForList().rightPop(waitingKey(queue), props.getBrpopTimeoutSeconds(), TimeUnit.SECONDS);
        if (json == null || json.isBlank()) {
            return null;
        }
        long reclaimAt = System.currentTimeMillis() + props.getReclaimSeconds() * 1000L;
        redis.opsForZSet().add(processingKey(queue), json, reclaimAt);
        try {
            JobPayload payload = objectMapper.readValue(json, JobPayload.class);
            return new ClaimedJob(queue, json, payload);
        } catch (Exception e) {
            redis.opsForZSet().remove(processingKey(queue), json);
            logger.error("Invalid job payload on queue {}: {}", queue, e.getMessage());
            return null;
        }
    }

    public void ack(ClaimedJob job) {
        redis.opsForZSet().remove(processingKey(job.queue()), job.rawJson());
        String ck = completedKey(job.queue());
        Long n = redis.opsForValue().increment(ck);
        if (n != null && n == 1L) {
            redis.expire(ck, Duration.ofHours(48));
        }
    }

    public void failOrRetry(ClaimedJob job, Exception error) {
        redis.opsForZSet().remove(processingKey(job.queue()), job.rawJson());
        JobPayload p = job.payload();
        p.setAttempts(p.getAttempts() + 1);
        if (p.getAttempts() < p.getMaxAttempts()) {
            try {
                dispatch(p);
                logger.warn("Job {} re-queued attempt {}/{}", p.getId(), p.getAttempts(), p.getMaxAttempts());
            } catch (Exception e) {
                persistFailed(p, error != null ? error : e);
            }
            return;
        }
        persistFailed(p, error);
    }

    public void persistFailed(JobPayload p, Exception error) {
        try {
            JobFailed row = new JobFailed();
            row.setUuid(p.getId());
            row.setQueue(p.getQueue());
            row.setJobType(p.getType());
            row.setPayload(objectMapper.writeValueAsString(p));
            row.setException(error != null ? truncate(error.toString(), 4000) : "unknown");
            row.setFailedAt(System.currentTimeMillis() / 1000);
            jobFailedMapper.insert(row);
        } catch (Exception e) {
            logger.error("Failed to persist failed job {}", p.getId(), e);
        }
    }

    public void reclaimStale() {
        long now = System.currentTimeMillis();
        for (String queue : props.getQueues().keySet()) {
            Set<String> stale = redis.opsForZSet().rangeByScore(processingKey(queue), 0, now);
            if (stale == null || stale.isEmpty()) {
                continue;
            }
            for (String json : stale) {
                Long removed = redis.opsForZSet().remove(processingKey(queue), json);
                if (removed != null && removed > 0) {
                    redis.opsForList().leftPush(waitingKey(queue), json);
                    logger.warn("Reclaimed stale job on queue {}", queue);
                }
            }
        }
    }

    public long waitingSize(String queue) {
        Long n = redis.opsForList().size(waitingKey(queue));
        return n != null ? n : 0L;
    }

    public long processingSize(String queue) {
        Long n = redis.opsForZSet().zCard(processingKey(queue));
        return n != null ? n : 0L;
    }

    public long completedThisHour(String queue) {
        String v = redis.opsForValue().get(completedKey(queue));
        if (v == null) {
            return 0L;
        }
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public long totalFailed() {
        return jobFailedMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>());
    }

    public List<Map<String, Object>> workloadSnapshot(Map<String, Integer> activeByQueue) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, String> labels = JobQueues.displayNames();
        for (Map.Entry<String, V2boardQueueProperties.QueueConfig> e : props.getQueues().entrySet()) {
            String q = e.getKey();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", q);
            row.put("display_name", labels.getOrDefault(q, q));
            long waiting = waitingSize(q);
            long processing = processingSize(q);
            row.put("jobs", waiting + processing);
            row.put("waiting", waiting);
            row.put("reserved", processing);
            row.put("processes", e.getValue().getConcurrency());
            row.put("active", activeByQueue.getOrDefault(q, 0));
            row.put("occupied", activeByQueue.getOrDefault(q, 0) > 0);
            rows.add(row);
        }
        return rows;
    }

    public JobPayload parse(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, JobPayload.class);
    }

    public String toJson(JobPayload p) throws JsonProcessingException {
        return objectMapper.writeValueAsString(p);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    public record ClaimedJob(String queue, String rawJson, JobPayload payload) {
    }
}
