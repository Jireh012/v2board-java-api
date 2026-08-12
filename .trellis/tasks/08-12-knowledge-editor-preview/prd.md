# 知识库文章编辑器与预览

## Goal

管理端新建/编辑知识库文章时，用 TinyMCE 可视化编辑正文，并提供预览；保存仍为 HTML，兼容现有占位符与 `copy()` 按钮。

## Background

- `AdminKnowledgeView`：textarea 编辑 `body`。
- 用户侧 `KnowledgeView` `v-html`；后端替换 `{{subscribeUrl}}` 等；前端已有全局 `copy`。

## Confirmed decisions

| 决策 | 选择 |
|------|------|
| 编辑器 | TinyMCE |
| 交付 | 管理端 UI（不改保存 API 契约） |
| 兼容 | 保留 HTML / 占位符 / onclick |

## Requirements

1. 编辑弹窗正文区使用 TinyMCE（自托管 npm，不依赖 Tiny Cloud API Key）。
2. 提供「编辑 / 预览」切换（源码可用 TinyMCE code 插件或等价方式）。
3. 预览用 `v-html`，样式接近用户侧阅读；占位符可用示例值替换仅用于预览（不写回）。
4. 预览页可点击 `copy(...)`（依赖全局 `copy`）。
5. 工具栏覆盖常用排版：标题、加粗、列表、链接、图片、代码、表格（合理子集即可）。
6. 保存仍提交 `body` HTML 字符串。

## Out of scope

- 后端改 body 存储格式（Markdown 等）
- 图片上传到本地面板（可用外链；本地上传另议）
- 用户侧阅读页大改

## Acceptance Criteria

- [ ] 新建/编辑弹窗可用 TinyMCE 编辑正文
- [ ] 可切换预览并看到接近用户侧的渲染
- [ ] 源码可查看/微调复杂 HTML
- [ ] 保存后用户端文章显示正常；`{{subscribeUrl}}` / `copy()` 仍可用
- [ ] 无 Tiny Cloud API Key 依赖（自托管）

## Open questions

（无）
