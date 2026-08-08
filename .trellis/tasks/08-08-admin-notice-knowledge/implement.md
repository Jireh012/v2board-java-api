# Implement: 管理端公告与知识库

## Checklist

1. **Backend**
   - `AdminKnowledgeController.save`：更新分支设置 `updatedAt=now`。
   - `fetch` 列表 `select` 增加 `language`、`sort`。
2. **Frontend API**
   - 修复 `sortAdminKnowledge` body 为 `JSON.stringify({ knowledgeIds: ids })`。
   - 如列表函数不够，增加 `fetchAdminKnowledgeById(id)` 调 `fetch?id=`。
3. **AdminNoticesView.vue**
   - 替换占位：列表 + modal CRUD + show/drop；风格对齐其他 admin 页。
4. **AdminKnowledgeView.vue**
   - 替换占位：列表 + category filter + drag sort + modal CRUD（编辑拉详情）。
5. **Optional quick win**
   - `KnowledgeView.vue` 若存在覆盖 `sort` 的 `updated_at` 重排，删除该重排以尊重后端顺序。
6. **Validate**
   - 手工：公告 CRUD→仪表盘可见；知识库 CRUD→用户 `/knowledge` 可见；拖拽后顺序正确。
   - UI typecheck 关注改动文件；API `mvn -DskipTests compile`。
7. **Check** → 通过后更新 spec（`admin-content.md` 或 `admin-user` 旁新建）→ commit。

## Validation commands

```bash
# API
export PATH="$HOME/.local/apache-maven/bin:$PATH"
mvn -q -DskipTests compile

# UI
cd ../v2board-ui && npx vue-tsc --noEmit 2>&1 | head -40  # 允许既有无关错误
```

## Risky files

- `AdminKnowledgeController.java` — list select 字段变更勿漏 id。
- `sortAdminKnowledge` — payload 形状必须与 `SortRequest` 对齐。
- 两 Vue 页 — HTML `v-html` 仅用户端；管理端勿对未转义用户输入做危险拼接。

## Rollback points

无 DB 变更；按文件回退即可。
