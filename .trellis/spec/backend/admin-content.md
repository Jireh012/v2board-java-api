# Admin Notice & Knowledge

> Executable contracts for `/api/v1/admin/notice` and `/api/v1/admin/knowledge`.

---

## Overview

Shares PHP `v2_notice` / `v2_knowledge`. JSON is SNAKE_CASE. Controllers talk to mappers directly (no service layer). Admin UI: `AdminNoticesView.vue`, `AdminKnowledgeView.vue`. User-facing HTML is rendered with `v-html` (`content` / `body`).

---

## Units / Fields

| Entity | Field | Notes |
|--------|-------|-------|
| Notice | `show` | 0/1; MySQL reserved — `@TableField("`show`")` |
| Notice | `tags` | comma-separated string |
| Notice | `img_url` | optional URL |
| Knowledge | `show` | 0/1; reserved column |
| Knowledge | `body` | HTML |
| Knowledge | `sort` | 1-based order; list ordered ASC |
| Knowledge | `language` | e.g. `zh-CN` |
| Knowledge | `category` | free string (not a separate entity) |

---

## Scenario: Notice admin CRUD

### 1. Scope / Trigger

- Trigger: Admin notices page must replace placeholder and drive existing APIs.

### 2. Signatures

```
GET  /api/v1/admin/notice/fetch
POST /api/v1/admin/notice/save   JSON Notice
POST /api/v1/admin/notice/show   form id
POST /api/v1/admin/notice/drop   form id
```

### 3. Contracts

| Item | Contract |
|------|----------|
| create | `id` null; require non-empty `title`; set `created_at`/`updated_at` |
| update | preserve `created_at`; refresh `updated_at` |
| show | toggle 0↔1 |
| optional clears | send `""` for `img_url`/`tags` (null skipped by MP update strategy) |

### 4. Validation & Error Matrix

| Condition | Error |
|-----------|-------|
| empty title on create | 标题不能为空 |
| missing id on show/drop | 参数有误 / 参数错误 |
| missing row | 公告不存在 |

### 5. Good / Base / Bad Cases

- **Good**: create with `show=1` → appears on user dashboard latest notice.
- **Base**: `img_url`/`tags` empty string clears.
- **Bad**: admin UI placeholder left live → operators cannot manage content.

### 6. Tests Required

- save create/update round-trip; show toggles; drop removes row.

### 7. Wrong vs Correct

#### Wrong

```ts
// leave AdminNoticesView as "功能开发中"
```

#### Correct

```ts
await saveAdminNotice({ title, content, img_url: '', tags: '', show: 1 })
```

---

## Scenario: Knowledge admin list / detail / sort

### 1. Scope / Trigger

- Trigger: Admin knowledge page + drag sort; user list must respect `sort`.

### 2. Signatures

```
GET  /api/v1/admin/knowledge/fetch
GET  /api/v1/admin/knowledge/fetch?id=
GET  /api/v1/admin/knowledge/category
POST /api/v1/admin/knowledge/save   JSON
POST /api/v1/admin/knowledge/show   form id
POST /api/v1/admin/knowledge/sort   JSON { knowledge_ids: Long[] }
POST /api/v1/admin/knowledge/drop   form id
```

### 3. Contracts

| Item | Contract |
|------|----------|
| list | lean: `id`, `title`, `category`, `show`, `updated_at`, `language`, `sort` — **no** `body` |
| detail `?id=` | full row including `body` |
| save update | always set `updated_at=now`; preserve `created_at` |
| save create | if `sort` null → `max(sort)+1` (or 1) |
| sort wire | **`knowledge_ids`** (SNAKE_CASE). Backend also accepts alias `knowledgeIds` |
| sort semantics | array order → `sort = 1..n` |
| user list | must not re-sort by `updated_at` (overrides admin order) |

### 4. Validation & Error Matrix

| Condition | Behavior |
|-----------|----------|
| sort empty ids | 保存失败 |
| missing knowledge | 知识不存在 |
| create insert fail | 创建失败 |

### 5. Good / Base / Bad Cases

- **Good**: drag reorder → POST `{ knowledge_ids: [...] }` → user `/knowledge` order matches.
- **Base**: edit opens `fetch?id=` then save with body HTML.
- **Bad**: `JSON.stringify(ids)` bare array → bind fail / silent no-op.

### 6. Tests Required

- list includes `language`/`sort`; update bumps `updated_at`; sort assigns sequential ranks.

### 7. Wrong vs Correct

#### Wrong

```ts
body: JSON.stringify(ids)
// or { knowledgeIds: ids } without SNAKE_CASE awareness
```

#### Correct

```ts
body: JSON.stringify({ knowledge_ids: ids })
```

```java
@JsonProperty("knowledge_ids")
@JsonAlias({ "knowledgeIds" })
private List<Long> knowledgeIds;
```
