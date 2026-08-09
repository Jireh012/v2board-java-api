# Job Queue (Redis workers)

> Persistent background jobs replacing process-only `@Async` for order/mail/telegram/stat/traffic.

---

## Scenario: Redis job broker (C3)

### 1. Scope / Trigger

- Trigger: Producers enqueue named queues; in-process workers BRPOP + processing ZSET; admin monitors workload/failed.
- Not Laravel Horizon protocol-compatible.

### 2. Signatures

```java
// JobDispatcher.dispatch*(...)
// RedisJobQueue.claimOnce / ack / failOrRetry / reclaimStale
// Admin: system/getQueueStats|getQueueWorkload|getFailedJobs|retryFailedJob|deleteFailedJob|clearFailedJobs
```

Queues: `order_handle`, `traffic_fetch`, `stat`, `send_email`, `send_telegram`.

### 3. Contracts

- Keys: `v2board:queue:{name}`, `v2board:queue:{name}:processing` (ZSET score=reclaim_at), completed meta hourly.
- Failed table: `v2_job_failed` (DDL `db/v2_job_failed.sql`).
- Config: `v2board.queue.*` (enabled, concurrency per queue, reclaim-seconds).
- `getSystemStatus.horizon=false`; use `queue_workers`.
- Push path enqueues traffic/stat only (no sync Redis hash loop on HTTP thread).

### 4. Validation & Error Matrix

| Condition | Result |
|-----------|--------|
| queue.enabled=false | dispatch no-ops / warn; workers not started |
| Unknown job type | fail → retry → failed table |
| Stale processing | reclaim schedule re-queues |

### 5–7

- Good: paid → order_handle → open; push → three jobs consumed.
- Bad: fake `horizon:true` without workers.
- Tests: `JobPayloadTest`, `JobQueuesTest`, `TrafficFetchJobHandlerTest`.

**UI**: `v2board-ui` Admin「队列监控」`/queue`.
