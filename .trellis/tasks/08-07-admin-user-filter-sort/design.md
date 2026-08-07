# Design: 用户列表筛选与流量排序

## Boundaries

| 层 | 改动 |
|----|------|
| Backend | `AdminUserController.fetch` 排序白名单 + `total_used` 表达式排序；`applyFilters` 增加虚拟 key `expired` |
| Frontend | `AdminUsersView.vue` 过期 tab、流量表头排序、`clearSearch`/`load` 传 sort；`fetchAdminUsers` 已支持 sort 参数，按需微调类型/默认值 |
| Spec | 实现后更新 `.trellis/spec/backend/admin-user.md`（Phase 3） |

跨仓：API 在 `v2board-java-api`，UI 在 `v2board-ui`。任务目录挂在 API 仓 Trellis 下。

## Contracts

### Query params（`GET /api/v1/admin/user/fetch`）

现有：`current`, `pageSize`, `sort`, `sort_type`, `filter[i][key|condition|value]`。

**Sort**

| `sort` | SQL |
|--------|-----|
| `created_at`（默认） | `ORDER BY created_at` |
| `total_used`（新增） | `ORDER BY (IFNULL(u,0)+IFNULL(d,0))` |
| 其它已有列名 | 保持现有 `orderBy` 行为；实现时对 `total_used` 单独分支，避免注入表达式 |

`sort_type`: `ASC` \| `DESC`（非法值回退 `DESC`）。

**Filter key `expired`（新增虚拟键）**

| condition | value | SQL（`now = unix_timestamp`） |
|-----------|-------|-------------------------------|
| `=` | `1` | `expired_at IS NOT NULL AND expired_at < {now}` |
| `=` | `0` | `(expired_at IS NULL OR expired_at >= {now})` |

其它 condition/value：忽略该 filter（与非法 key 一致）。`expired` 加入 `ALLOWED_FILTER_KEYS`。

**备注**：继续 `key=remarks` + `condition=模糊` → `LIKE %value%`（已有，回归即可）。

### Frontend state

```
expiredQuick: 'all' | 0 | 1
sortKey: 'created_at' | 'total_used'
sortType: 'ASC' | 'DESC'
```

- `buildFilters`：`expiredQuick !== 'all'` 时 push `{ key: 'expired', condition: '=', value: String(expiredQuick) }`。
- `load`：`fetchAdminUsers(..., sortKey, sortType)`。
- 流量表头：若当前非 `total_used`，点击设为 `total_used` + `DESC`；若已是，则翻转 `sortType`。
- 清除：`expiredQuick='all'`，`sortKey='created_at'`，`sortType='DESC'`。

## Data flow

```
UI tabs/search/header
  → OrderFilter[] + sort/sort_type
  → fetchAdminUsers
  → AdminUserController.fetch
      → applyFilters (incl. expired, remarks)
      → orderBy / last(LIMIT)
      → { data, total } with total_used
```

## Compatibility

- 不改 DB schema。
- 未传 `expired` / `sort=total_used` 时与现网行为一致。
- 批量 ban / dumpCSV 复用 `applyFilters`，自动获得过期筛选能力（可接受；CSV 仍按 id 排序）。

## Trade-offs

| 方案 | 取舍 |
|------|------|
| 虚拟 filter `expired` vs 独立 query param | 虚拟 key 复用现有 filter 管道与 ban/CSV，UI 改动小 |
| `ORDER BY (u+d)` vs 冗余列 | 不改表；大表全表排序可接受（管理端分页），不加索引迁移 |

## Rollback

- 回退 `AdminUserController` 与 `AdminUsersView.vue`（及如有改动的 `admin.ts`）即可；无数据迁移。
