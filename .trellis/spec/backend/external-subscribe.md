# External Subscribe Sync Contracts

> Executable contracts for third-party subscription sync (`v2_external_subscribe_*`).

---

## Scenario: Sync status lifecycle (`last_sync_status`)

### 1. Scope / Trigger

- Trigger: Durable DB field `last_sync_status` is paired with an in-memory `AtomicBoolean` lock.
- Restart / process kill clears the lock but **does not** clear DB `running`, so admin UI can stick on「同步中」/ `RUNNING`.
- Cross-layer: Service → MySQL → Admin list API → Vue badge.

### 2. Signatures

**Tables** (`src/main/resources/db/v2_external_subscribe.sql`):

- `v2_external_subscribe_source.last_sync_status` — `varchar(32)`, values: `success` | `failed` | `running` | null
- `v2_external_subscribe_source.last_sync_message` — `varchar(1024)`
- `v2_external_subscribe_source.last_sync_at` — unix seconds

**Service** (`ExternalSubscribeSyncService`):

```java
@EventListener(ApplicationReadyEvent.class)
void onApplicationReady();                 // recoverInterruptedSyncs("服务重启，同步中断")

int recoverInterruptedSyncs(String message); // UPDATE all status=running → failed

void syncAll();                            // acquire lock → recover → sync enabled sources
void syncOne(Long id);                     // acquire lock → recover → sync one
```

**Admin API** (`AdminExternalSubscribeController`):

| Method | Path | Behavior |
|--------|------|----------|
| GET | `/api/v1/admin/external-subscribe/fetch` | Returns `last_sync_status`, `last_sync_message`, `last_sync_at` |
| POST | `/api/v1/admin/external-subscribe/sync?id=` | Starts `syncOne` on a background thread; returns `true` immediately |
| POST | `/api/v1/admin/external-subscribe/sync-all` | Starts `syncAll` on a background thread |

### 3. Contracts

**Status transitions**

| From | To | When |
|------|----|------|
| * | `running` | Start of `syncSourceInternal` (`message` = `同步中`) |
| `running` | `success` | Parse/probe finished (`message` = summary) |
| `running` | `failed` | Exception, or recovery after restart / before new sync |
| `running` (zombie) | `failed` | `ApplicationReadyEvent` or after lock acquired |

**Lock**

- Process-local `AtomicBoolean running`.
- Single-instance assumption: after lock is acquired, any DB row still `running` is a dead job → mark `failed`.
- Concurrent `syncOne` while lock held → `BusinessException(500, "同步任务正在进行中，请稍后再试")`.
- Concurrent `syncAll` while lock held → skip (log only).

**List row fields (snake_case JSON)**

| Field | Type | Notes |
|-------|------|-------|
| `last_sync_status` | string \| null | `success` / `failed` / `running` |
| `last_sync_message` | string \| null | Truncated to ≤1000 chars on write |
| `last_sync_at` | number \| null | Unix seconds |

### 4. Validation & Error Matrix

| Condition | Result |
|-----------|--------|
| Source id missing | `BusinessException(500, "订阅源不存在")` |
| Sync already in progress (`syncOne`) | `BusinessException(500, "同步任务正在进行中，请稍后再试")` |
| Empty URL | finish `failed` — `订阅地址为空` |
| sing-box unavailable | finish `failed` — configure `v2board.external-subscribe.sing-box-path` |
| JVM restart mid-sync | On next boot: all `running` → `failed` / `服务重启，同步中断` |
| New sync after crash without boot recovery | After lock: leftover `running` → `failed` / `上次同步异常中断` |

### 5. Good / Base / Bad Cases

- **Good**: Sync completes → status `success`, message like `解析 N 个，连通 M 个`.
- **Base**: Empty parse → `success` with `未解析到节点`, nodes for source cleared.
- **Bad**: Kill process while status=`running` and **no** startup recovery → UI stuck on RUNNING (forbidden regression).
- **Bad**: Swallow convert/sync errors without updating `last_sync_status` → same stuck UI.

### 6. Tests Required

- **Unit**: `recoverInterruptedSyncs` updates every `running` row to `failed` with given message; returns count.
- **Unit**: `syncOne` when lock held throws `BusinessException` with「同步任务正在进行中」.
- **Integration / boot**: Insert source with `last_sync_status=running`, publish `ApplicationReadyEvent` (or start context) → assert status=`failed` and message contains `服务重启`.
- **Integration**: Happy-path sync ends with `success` (mock fetcher + probe).

### 7. Wrong vs Correct

#### Wrong

```java
// Set DB running, rely only on in-memory AtomicBoolean finally block
source.setLastSyncStatus("running");
sourceMapper.updateById(source);
// ... long probe ...
// Process killed → DB forever "running", UI shows 同步中
```

#### Correct

```java
@EventListener(ApplicationReadyEvent.class)
public void onApplicationReady() {
    recoverInterruptedSyncs("服务重启，同步中断");
}

public void syncOne(Long id) {
    if (!running.compareAndSet(false, true)) {
        throw new BusinessException(500, "同步任务正在进行中，请稍后再试");
    }
    try {
        recoverInterruptedSyncs("上次同步异常中断");
        // then syncSourceInternal...
    } finally {
        running.set(false);
    }
}
```

---

## Design Decision: Durable status + process lock

**Context**: Sync is long-running (fetch + sing-box probe) and exposed asynchronously via admin API threads.

**Options**:
1. In-memory status only — lost on restart; list API cannot show progress.
2. DB status without recovery — UI can stick on `running` after crash.
3. DB status + startup/pre-sync recovery — chosen.

**Decision**: Persist `running/success/failed` in MySQL; recover zombies on `ApplicationReadyEvent` and whenever a new sync acquires the process lock.
