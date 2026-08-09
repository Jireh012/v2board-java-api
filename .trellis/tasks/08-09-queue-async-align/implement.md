# Implement — C3 Redis queues

## Checklist

### A. Core broker (java-api)

1. [x] DDL `v2_job_failed` + entity/mapper
2. [x] `v2board.queue.*` properties + defaults in yml
3. [x] `JobPayload` / `RedisJobQueue` (dispatch, BRPOP claim, ack, fail→DB)
4. [x] Reclaim sweeper `@Scheduled`
5. [x] `JobWorkerManager` starts N consumers per queue from config
6. [x] Handlers registry: Order / TrafficFetch / StatUser / StatServer / SendEmail / SendTelegram

### B. Wire producers

7. [x] `OrderService` paid + `OrderSchedule` → enqueue `order_handle`
8. [x] `UniProxyController` push → enqueue `traffic_fetch` + `stat`
9. [x] `MailService.sendEmail` → enqueue; keep `sendEmailSync` for admin test
10. [x] `TelegramService.sendMessageAsync` → enqueue
11. [x] Remove `@Async` on migrated methods

### C. Admin API + catalog

12. [x] `AdminSystemController`: stats / workload / failed / retry / delete / clear
13. [x] Honest `getSystemStatus` (`queue_workers`, `horizon: false`)
14. [x] `PanelApiActionCatalog`

### D. UI (v2board-ui)

15. [x] `AdminQueueView.vue` + router + sidebar
16. [x] API client for workload/failed/retry
17. [x] Chinese labels per design table

### E. Spec / tests

18. [x] Unit: JobPayload / JobQueues / coerceTrafficMap
19. [x] `.trellis/spec/backend/job-queue.md`
20. [ ] Ops: apply DDL on deploy DB; smoke monitor page

## Validation

```bash
# API
cd /Users/jirehlam/Repos/v2board-java-api
mvn -Dtest=JobQueue*,AdminSystem*,OrderService*,UserService*Traffic* test
# apply DDL to local MySQL once
# manual: push node traffic → Redis queue length moves; kill -9 app → reclaim → processed

# UI
cd /Users/jirehlam/Repos/v2board-ui
npm run build
```

## Risky files

- `UniProxyController` push path (node latency / correctness)
- `OrderService` paid → open subscription
- Redis key collisions with existing cache keys
- `PanelApiActionCatalog` miss → 404 on new admin actions

## Rollback

- Revert release **or** `v2board.queue.enabled=false` if sync fallback implemented; else revert producers to `@Async`.
