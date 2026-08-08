# 管理端公告与知识库完善

## Goal

管理员可在后台完整管理公告与知识库文章（列表、创建/编辑、显隐、删除；知识库另含分类与排序），替代当前「功能开发中」占位页，打通已有后端 API。

## Background

- 后端 `AdminNoticeController` / `AdminKnowledgeController` 已提供 CRUD / show / drop；知识库另有 category、sort。
- 前端路由 `/admin/notices`、`/admin/knowledge` 与侧栏已挂载，页面为占位；`admin.ts` 已有 API 封装但未使用。
- 用户确认：本轮不做用户端公告列表 / `img_url` / `tags` 展示增强。
- 上一任务 `08-07-admin-user-filter-sort` 已 finish（会话指针已切换）。

## Confirmed Facts

| 项 | 现状 |
|----|------|
| Notice 字段 | `title`, `content`, `show`, `img_url`, `tags`, timestamps |
| Knowledge 字段 | `category`, `title`, `language`, `show`, `sort`, `body`, timestamps |
| 知识库列表 fetch | 仅返回 `id/title/category/show/updated_at`（无 `body`/`language`/`sort`）；有 `?id=` 详情 |
| 排序契约 | 后端要 `{ knowledgeIds: number[] }`；前端 `sortAdminKnowledge` 误发裸数组 |
| Knowledge update | 未刷新 `updated_at` |
| 正文形态 | 用户端 `v-html` 渲染 HTML；管理端他页多用 textarea |

## Requirements

1. **R1 公告管理页**：列表（标题、标签、图片、显隐、时间）；新建/编辑弹窗（标题、内容 HTML、img_url、tags、初始 show）；行内显隐切换；删除确认。
2. **R2 知识库管理页**：列表（分类、标题、语言、显隐、更新时间）；拖拽排序（对齐套餐页模式）并调用 sort API；新建/编辑（分类可选手填、标题、language、body HTML、show）；编辑时 `fetch?id=` 拉正文；显隐、删除。
3. **R3 API 契约修复**：`sortAdminKnowledge` 发送 `{ knowledgeIds }`；知识库 `save` 更新时写 `updated_at`；列表建议附带 `language`/`sort` 便于管理展示（可选但推荐）。
4. **R4 视觉一致**：沿用现有 admin 页面结构（page-header、panel、table、modal、filter tabs），不引入富文本编辑器依赖。

## Acceptance Criteria

- [ ] AC1：可创建/编辑/显隐/删除公告；`show=1` 的公告仍可出现在用户仪表盘最新公告。
- [ ] AC2：可创建/编辑/显隐/删除知识库文章；分类下拉含已有分类且允许新分类字符串。
- [ ] AC3：拖拽调整知识库顺序后刷新，用户端列表顺序与 `sort` 一致（不再被错误 payload 破坏）。
- [ ] AC4：编辑知识库时能看到并保存完整 `body`；更新后 `updated_at` 变化。
- [ ] AC5：占位文案消失；空列表有明确空态与「新建」入口。

## Out of Scope

- 用户端公告历史页、仪表盘展示 `img_url`/`tags`。
- 独立分类实体 CRUD、多语言切换 UI、富文本编辑器、Markdown。
- 公告排序、公告/知识库分页（全量列表可接受）。
- 拆出 Service 层或新增自动化测试脚手架（除非实现中顺手极低成本）。

## Key Decisions

| 决策 | 选择 |
|------|------|
| MVP 范围 | 仅管理端闭环 + 契约修复 |
| 正文编辑 | textarea 编辑 HTML（与现有 admin 一致） |
| 知识库排序 UI | 行拖拽，对齐 `AdminPlansView` |
| 语言字段 | 文本输入（如 `zh-CN`），默认 `zh-CN` |

## Open Questions

（无阻塞项）
