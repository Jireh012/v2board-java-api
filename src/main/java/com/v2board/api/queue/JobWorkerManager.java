package com.v2board.api.queue;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class JobWorkerManager {

    private static final Logger logger = LoggerFactory.getLogger(JobWorkerManager.class);

    @Autowired
    private RedisJobQueue jobQueue;

    @Autowired
    private V2boardQueueProperties props;

    @Autowired
    private List<JobHandler> handlers;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, AtomicInteger> activeByQueue = new ConcurrentHashMap<>();
    private ExecutorService pool;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!props.isEnabled()) {
            logger.info("v2board.queue.enabled=false; workers not started");
            jobQueue.setWorkersRunning(false);
            return;
        }
        Map<String, JobHandler> byType = handlers.stream()
                .collect(Collectors.toMap(JobHandler::type, Function.identity(), (a, b) -> a));
        int total = props.getQueues().values().stream()
                .mapToInt(V2boardQueueProperties.QueueConfig::getConcurrency)
                .sum();
        AtomicInteger seq = new AtomicInteger();
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "job-worker-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        pool = Executors.newFixedThreadPool(Math.max(1, total), tf);
        running.set(true);
        jobQueue.setWorkersRunning(true);
        for (Map.Entry<String, V2boardQueueProperties.QueueConfig> e : props.getQueues().entrySet()) {
            String queue = e.getKey();
            activeByQueue.putIfAbsent(queue, new AtomicInteger());
            int n = e.getValue().getConcurrency();
            for (int i = 0; i < n; i++) {
                pool.submit(() -> loop(queue, byType));
            }
            logger.info("Started {} worker(s) for queue {}", n, queue);
        }
    }

    private void loop(String queue, Map<String, JobHandler> byType) {
        while (running.get()) {
            try {
                RedisJobQueue.ClaimedJob job = jobQueue.claimOnce(queue);
                if (job == null) {
                    continue;
                }
                AtomicInteger active = activeByQueue.get(queue);
                if (active != null) {
                    active.incrementAndGet();
                }
                try {
                    JobHandler handler = byType.get(job.payload().getType());
                    if (handler == null) {
                        throw new IllegalStateException("No handler for type " + job.payload().getType());
                    }
                    handler.handle(job.payload());
                    jobQueue.ack(job);
                } catch (Exception ex) {
                    logger.error("Job {} failed: {}", job.payload().getId(), ex.getMessage(), ex);
                    jobQueue.failOrRetry(job, ex);
                } finally {
                    if (active != null) {
                        active.decrementAndGet();
                    }
                }
            } catch (Exception e) {
                if (running.get()) {
                    logger.error("Worker loop error on {}: {}", queue, e.getMessage());
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    public Map<String, Integer> activeSnapshot() {
        Map<String, Integer> out = new ConcurrentHashMap<>();
        activeByQueue.forEach((k, v) -> out.put(k, v.get()));
        return out;
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        jobQueue.setWorkersRunning(false);
        if (pool != null) {
            pool.shutdownNow();
            try {
                pool.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
