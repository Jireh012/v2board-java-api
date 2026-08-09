# Design: External subscribe pre-proxy

## Boundaries

| Layer | Change |
|-------|--------|
| DB | `v2_external_subscribe_source.pre_proxy_enable` tinyint 0/1 |
| Model / Admin API | Persist + return `pre_proxy_enable` |
| Sync | Before `fetcher.fetch`, optionally run via auto-picked node proxy |
| Fetcher | Accept optional `java.net.Proxy` (or overload) |
| Probe helper | Extract/reuse “run sing-box mixed with outbound” for fetch lifetime |
| UI | Toggle on create/edit form (+ optional list badge) |

## Contracts

### Schema

```sql
ALTER TABLE `v2_external_subscribe_source`
  ADD COLUMN `pre_proxy_enable` tinyint NOT NULL DEFAULT 0
  COMMENT '0直连 1自动前置代理' AFTER `enable`;
```

### Admin save/fetch

- Field: `pre_proxy_enable` number `0|1` (Toggle writes 0/1).
- No `pre_proxy_node_id`.

### Auto pick

```
candidates = nodes where reachable=1
  AND singbox_outbound not empty
  AND source_id != currentSourceId
  AND parent source enable=1 (optional but recommended)
ORDER BY sort ASC, id ASC
LIMIT 1
```

If empty → fail sync: `前置代理已开启但无可用节点`.

### Fetch via proxy

1. Parse chosen `singbox_outbound` JSON → Map.
2. Allocate local port; write temp sing-box config (same shape as probe: mixed inbound → final outbound).
3. Start `sing-box run -c ...`; wait port open.
4. `ExternalSubscribeFetcher.fetch(url, Proxy.HTTP 127.0.0.1:port)`.
5. Destroy process + delete temp file in `finally`.
6. Remaining parse/filter/probe unchanged (probe still uses its own short-lived proxies).

### Validation

| Condition | Behavior |
|-----------|----------|
| `pre_proxy_enable=0` | Direct fetch |
| `=1`, no candidate | `failed` message |
| `=1`, sing-box missing | Existing sing-box path error |
| `=1`, proxy fetch HTTP error | `failed` with cause |

## Tradeoffs

| Choice | Why |
|--------|-----|
| Auto vs manual pick | Matches ops UX; less config drift |
| Exclude self-source | Anti-loop; enables cold-start via other sources |
| Stable sort | Deterministic debugging |
| No silent direct fallback | Honors operator intent |

## Rollout

1. Apply ALTER on deploy DB.
2. Ship API + UI; default `0` safe.
3. Rollback: set all `0` or revert code; column can remain.
