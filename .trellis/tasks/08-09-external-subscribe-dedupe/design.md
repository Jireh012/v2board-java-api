# Design: 第三方订阅逻辑键去重

## Boundaries

| Layer | Change |
|-------|--------|
| Parser | `fingerprint()` → 逻辑键派生；抽出 `logicalKey(outbound)` 供复用 |
| NodeService | `listReachableAsServerMaps`：跨源按逻辑键去重 + 同名编号，并回写 name/clash/tag/uri |
| Sync | 无流程变更；依赖新 fingerprint 自动 upsert/清理 |
| Admin API | 无变更（按源读库） |
| ClientController | 仍后置 `applyNodeSecurityMarkers`；编号必须在其之前完成 |

## Logical key

```
type.lower + "|" + server.lower.trim + "|" + port + "|" + credential
```

- `credential` = `uuid` if non-blank else `password` else `""`
- Fingerprint stored in DB: SHA-256 hex 前 32 位（与现格式兼容），输入为逻辑键字符串（UTF-8）
- Parser `dedupe` 仍按 fingerprint `putIfAbsent` → 同源解析序第一条胜出

## Delivery flow

```
DB reachable (enable sources, sort ASC, id ASC)
  → toServerMap
  → LinkedHashMap putIfAbsent(logicalKey)   // 跨源保留第一条
  → rename colliding display names (base name → name1, name2, …)
  → sync clash_proxy.name / singbox_outbound.tag / share_uri fragment
  → return list
ClientController.applyNodeSecurityMarkers(⚠️)
```

## Name numbering

- Group by exact display `name` after dedupe.
- If group size == 1: unchanged.
- If size > 1: assign `name + (i+1)` in list order (1-based).
- Do not strip existing trailing digits from upstream names; only append our counter when colliding.

## Compatibility / rollout

- 下次全量/单源同步后，旧「整 JSON」指纹行会因 `seen` 不含而被删除；无需手工 migration。
- 未同步前：下发侧逻辑键去重仍立即生效（即使库内仍有 TLS 变体多行）。
- 回滚：恢复旧 fingerprint 算法 + 去掉 NodeService 去重/编号即可；再同步会按旧键重新灌库。

## Tradeoffs

| Option | Pros | Cons |
|--------|------|------|
| 仅下发去重 | 改动面小 | 探测浪费、库膨胀 |
| **同步指纹=逻辑键 + 下发跨源去重（选定）** | 库与探测收敛 | 丢失 TLS 变体多样性（可接受） |

## Risks

- 无 uuid/password 的节点仅靠 type+server+port 合并，可能过度合并 → 接受（极少见）。
- host 大小写归一后合并 → 期望行为。
