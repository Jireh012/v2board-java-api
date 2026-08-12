# Implement: 知识库 TinyMCE + 预览

## Checklist

1. [x] `npm i tinymce @tinymce/tinymce-vue`
2. [x] 配置 Vite/自托管资源加载（skins/icons/models）
3. [x] 新建 `KnowledgeBodyEditor.vue`（编辑/预览、占位符插入、extended_valid_elements）
4. [x] `AdminKnowledgeView` 接入；更新 modal 文案
5. [x] `vue-tsc` / `npm run build` 通过
6. [ ] 手工：打开含 `copy('{{subscribeUrl}}')` 的文章 → 编辑不丢属性 → 预览可复制 → 保存后用户侧正常

## Validation

```bash
cd /Users/jireh/Repos/v2board-ui
npm run type-check
npm run build
```

## Before start

- [x] prd / design / implement
- [ ] jsonl curated
- [ ] 用户批准规划摘要
