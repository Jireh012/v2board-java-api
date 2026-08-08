# Design: 管理端公告与知识库

## Boundaries

| 层 | 改动 |
|----|------|
| Backend | `AdminKnowledgeController`：update 写 `updated_at`；list `select` 增加 `language`/`sort` |
| Frontend API | `sortAdminKnowledge` → `{ knowledgeIds: ids }`；如需可加 `fetchAdminKnowledgeById` |
| Frontend UI | 重写 `AdminNoticesView.vue`、`AdminKnowledgeView.vue` |
| Spec | Phase 3 新增 `.trellis/spec/backend/admin-content.md`（或并入既有指南） |

跨仓：API `v2board-java-api`，UI `v2board-ui`。任务挂在 API 仓 Trellis。

## Contracts

### Notice（已有，UI 对接）

```
GET  /api/v1/admin/notice/fetch
POST /api/v1/admin/notice/save     JSON Notice
POST /api/v1/admin/notice/show     form id
POST /api/v1/admin/notice/drop     form id
```

- `tags`：逗号分隔字符串。
- `img_url`：可选 URL 字符串。
- `show`：0/1；创建默认 0 或 1（UI 默认 1，与常见「新建即发布」一致——实现时默认 **1**）。

### Knowledge

```
GET  /api/v1/admin/knowledge/fetch           // list (lean)
GET  /api/v1/admin/knowledge/fetch?id=       // full row
GET  /api/v1/admin/knowledge/category
POST /api/v1/admin/knowledge/save            JSON
POST /api/v1/admin/knowledge/show            form id
POST /api/v1/admin/knowledge/sort            JSON { knowledgeIds: Long[] }
POST /api/v1/admin/knowledge/drop            form id
```

**List fields after change**: `id`, `title`, `category`, `show`, `updated_at`, `language`, `sort`.

**Sort payload (fix FE)** — Jackson global SNAKE_CASE:

```json
{ "knowledge_ids": [3, 1, 2] }
```

Backend `SortRequest` binds `knowledge_ids` (`@JsonAlias("knowledgeIds")` also accepted).

Backend assigns `sort = 1..n` in array order.

## UI shape

### AdminNoticesView

- Header +「新建公告」
- Table: 标题 / 标签 / 图片缩略或短链 / 状态 / 更新时间 / 操作（编辑、显隐、删除）
- Modal: title, content textarea, img_url, tags, show toggle
- Confirm delete dialog（复用现有 toast + confirm 模式）

### AdminKnowledgeView

- Header +「新建文章」
- Optional category filter chips from `fetchAdminKnowledgeCategory`
- Table with drag handle（模式抄 `AdminPlansView`）；列：分类 / 标题 / 语言 / 状态 / 更新时间 / 操作
- Modal: category (datalist/select+input), title, language, body textarea, show
- Edit: `fetchAdminKnowledge` list 不够 → `GET fetch?id=` 取 body

## Data flow

```
Admin UI → admin.ts → Admin*Controller → Mapper → v2_notice / v2_knowledge
User dashboard/knowledge (unchanged) 继续读 show=1 数据
```

## Compatibility

- 不改表结构。
- 用户端 KnowledgeView 若仍按 `updated_at` 重排会覆盖 sort——**本任务不改用户端**（Out of Scope）；管理端 sort 仍正确入库，后续可单开修复。若改动成本极低（删前端重排一行）可顺手修，记在 implement 可选勾。

## Trade-offs

| 选择 | 理由 |
|------|------|
| textarea HTML | 零新依赖；与用户端 v-html 一致 |
| 全量列表无分页 | 管理数据量通常小；对齐现网 API |
| 拖拽排序 | 已有套餐页模式可复用 |

## Rollback

回退两个 Vue + admin.ts sort 修复 + KnowledgeController 小改；无迁移。
