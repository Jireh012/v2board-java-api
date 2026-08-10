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

> **UI**: Admin「上次同步」must show **status + `last_sync_at` time + message** together. Do not use `message || fmtTime(at)` — when message exists the timestamp disappears.

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

---

## Scenario: Drop upstream info pseudo-nodes

### 1. Scope / Trigger

- Upstream subscribe often injects fake proxies named like `剩余流量：…` / `套餐到期：…` for client UI.
- Third-party ingest must **not** store or emit these into user subscribe.

### 2. Contract

- Detector: `ExternalInfoNode.isInfoName` / `removeFrom`
- Sync: after name filters, drop info nodes before probe/upsert (next sync also deletes previously stored ones via `seen`)
- Delivery: `listReachableAsServerMaps` and admin `listBySourceId` skip info names as a safety net

### 3. Wrong vs Correct

#### Wrong

```java
// Keep "剩余流量：58.73 GB" as a reachable trojan node in external list
```

#### Correct

```java
ExternalInfoNode.removeFrom(parsed); // before probeAll / upsert
```

---

## Scenario: Subscribe fetch User-Agent

### 1. Scope / Trigger

- Some upstream panels return **HTTP 403** with body `The User-Agent has been blocked` for non-client UAs.
- Clients like v2rayN / Clash Verge still work against the same URL.

### 2. Contract

`ExternalSubscribeFetcher` must request with common client UAs (e.g. `clash-verge/…`, `ClashMetaForAndroid/…`, `v2rayN/…`) and **retry the next UA on 403 UA-block**, not a custom `v2board-java-api/…` string alone.

### 3. Wrong vs Correct

#### Wrong

```java
conn.setRequestProperty("User-Agent", "v2board-java-api/external-subscribe");
```

#### Correct

```java
// try clash-verge → ClashMeta → v2rayN; continue only on UA-block 403
```

---

## Scenario: Per-source display-name filters (`name_filters`)

### 1. Scope / Trigger

- Admin edits source → saves `name_filters` JSON → next sync rewrites node display names.

### 2. Storage / API

- Column: `v2_external_subscribe_source.name_filters` (`json`, nullable / `[]`)
- Wire: `name_filters: [{ pattern, replacement, regex }]`
- `replacement` may be `""` (delete match)
- `regex=true` → Java `Matcher.replaceAll` (supports `$1`); invalid pattern → **reject on save**

### 3. Apply

- After parse, before probe/upsert: `ExternalNameFilter.applyFiltersToParsed`
- Updates `name`, outbound `tag`, and `share_uri` via `ExternalNodeIdentity.rewriteShareUriName`
- `rewriteShareUriName` updates `#fragment` **and** `vmess://` base64 JSON `ps` (many clients read `ps`, not the fragment)
- Delivery (`GeneralHandler` external path) must call the same helper — fragment-only rewrite leaves old `ps`
- Blank result after filters → keep original name
- Does **not** change logical-key fingerprint

### 4. Wrong vs Correct

#### Wrong

```java
// Apply filters only in listReachableAsServerMaps — admin node list stays unfiltered
// Or rewrite only share_uri #fragment for vmess — clients still show old ps
int hash = share.indexOf('#');
share = base + "#" + encode(name);
```

#### Correct

```java
ExternalNameFilter.applyFiltersToParsed(parsed, ExternalNameFilter.fromJson(source.getNameFilters()));
// then probe + upsert
// share_uri / GeneralHandler: ExternalNodeIdentity.rewriteShareUriName(share, name)
```

---

## Scenario: Logical-key fingerprint and subscribe dedupe

### 1. Scope / Trigger

- Sync parse stores `fingerprint` from **logical identity**, not full outbound JSON.
- Subscribe merge must not emit duplicate third-party nodes across enabled sources.

### 2. Signatures

`ExternalNodeIdentity`:

```java
String logicalKey(Map<String, Object> outbound);
// type.lower|server.lower|port|uuid-or-password
String fingerprint(Map<String, Object> outbound); // SHA-256 hex[:32] of logicalKey
void applyDisplayName(Map<String, Object> server, String name);
```

`ExternalSubscribeNodeService.listReachableAsServerMaps()`:

1. Load reachable nodes for enabled sources (`sort ASC`, `id ASC`)
2. Dedupe by `logicalKey` — **first wins**
3. If display names collide after dedupe, rename to `name1`, `name2`, … and sync clash/tag/share_uri
4. Return list (Controller then applies `⚠️ ` markers)

### 3. Contracts

| Piece | Behavior |
|-------|----------|
| Credential | Prefer `uuid`; else `password`; else empty |
| Within-source sync | Same logical key → one row (`uk_source_fingerprint` + parser `dedupe`) |
| Cross-source | Delivery-time dedupe only; admin per-source list stays raw DB rows |
| Name numbering | Only when ≥2 remaining nodes share exact `name`; unique names unchanged |
| Markers | Numbering **before** `applyNodeSecurityMarkers` |

### 4. Wrong vs Correct

#### Wrong

```java
// fingerprint = SHA256(full outbound JSON including tag/tls utls)
```

#### Correct

```java
fingerprint = SHA256(logicalKey(outbound)); // ignores tag / TLS client fingerprint variants
```

---

## Scenario: Pre-proxy fetch (`pre_proxy_enable`)

### 1. Scope / Trigger

- Admin toggle on source: fetch remote subscribe URL via an auto-picked library node (HTTP proxy through temporary sing-box mixed).
- UI exposes **only** the toggle — no manual node picker.

### 2. Signatures

```
v2_external_subscribe_source.pre_proxy_enable  -- tinyint 0|1, default 0
Admin save/fetch JSON field: pre_proxy_enable
ExternalSubscribeSyncService.fetchSubscribeContent(source)
SingBoxProbeService.openHttpProxy(outbound) -> LocalHttpProxySession (AutoCloseable)
ExternalSubscribeFetcher.fetch(url, Proxy)   -- null Proxy = direct
```

### 3. Contracts

| `pre_proxy_enable` | Behavior |
|--------------------|----------|
| `0` / null | Direct `fetcher.fetch(url)` |
| `1` | Pick first reachable node from **other enabled sources** (`sort ASC`, `id ASC`); open local HTTP proxy; `fetch(url, proxy)` |

- Empty candidate set → sync `failed`, message `前置代理已开启但无可用节点` (no silent direct fallback).
- Exclude current `source_id` (anti-loop / cold-start via other sources).
- Probe phase unchanged (still uses its own short-lived proxies).

### 4. Wrong vs Correct

#### Wrong
```java
if (preProxy && pick() == null) return fetcher.fetch(url); // silent direct
```

#### Correct
```java
if (preProxy && pick() == null) throw new IllegalStateException("前置代理已开启但无可用节点");
```

---

## Related: Client node naming

Reachable external nodes enter subscribe output via `ExternalSubscribeNodeService.listReachableAsServerMaps()` (`type=external`).

Panel vs external **display name prefixes** (`🔒 ` / `⚠️ `) and Clash/Sing-box/share-URI sync rules live in [subscribe-delivery.md](./subscribe-delivery.md) — do not only mutate `server.name`.
