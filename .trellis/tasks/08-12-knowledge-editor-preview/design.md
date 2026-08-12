# Design: 知识库 TinyMCE + 预览

## Scope

仅 `v2board-ui` 管理端 `AdminKnowledgeView`（及可复用小组件）。API 不变。

## Dependencies

```bash
npm i tinymce @tinymce/tinymce-vue
```

Self-host: copy TinyMCE skins/plugins via Vite public or `import 'tinymce/tinymce'` + model imports（官方 Vue 集成文档）。禁止依赖 `apiKey` CDN 云端。

## UI

编辑弹窗正文区：

```
[编辑] [预览]
┌─────────────────────────────┐
│ TinyMCE (edit mode)         │
│ 或 preview pane (v-html)    │
└─────────────────────────────┘
快捷：插入订阅地址占位符按钮 → `{{subscribeUrl}}`
```

- **编辑**：`<Editor v-model="form.body" :init="tinymceInit" />`
- **预览**：`v-html="previewHtml"`，`previewHtml` = 将 `{{subscribeUrl}}` / `{{urlEncodeSubscribeUrl}}` / `{{safeBase64SubscribeUrl}}` / `{{subscribeToken}}` / `{{siteName}}` 换成示例字符串（仅预览）。
- TinyMCE `plugins`: `lists link image table code codesample fullscreen`（按体积取舍）
- `valid_elements` / `extended_valid_elements`：允许 `a[onclick|class|href|target|rel]` 等，避免剥掉 `onclick="copy(...)"`

## Component split

- `src/components/admin/KnowledgeBodyEditor.vue` — TinyMCE + 编辑/预览 tabs + 插入占位符
- `AdminKnowledgeView.vue` — 用组件替换 textarea

## Trade-offs

| 选项 | 选择 | 原因 |
|------|------|------|
| CDN Cloud | 否 | 免 API Key、离线可控 |
| 强制净化 HTML | 轻度保留 onclick | 兼容现有教程 |
| 图片上传 | 外链 | MVP 范围 |

## Rollback

移除组件、恢复 textarea、卸依赖。
