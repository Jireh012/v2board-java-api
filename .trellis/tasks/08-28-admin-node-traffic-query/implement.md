# Implement — 管理后台查询节点流量

## Checklist

### A. API（v2board-java-api）

1. [x] `AdminStatController#getStatServer`：校验日期、查 `v2_stat_server`、区间合计、节点名
2. [x] `PanelApiActionCatalog` 增加 `stat/getStatServer`
3. [x] 单测：日期校验、闭区间、空结果、字节不转 GB
4. [x] 更新 `.trellis/spec/backend/admin-stat.md`

### B. UI（v2board-ui）

5. [x] `fetchStatServer` + 类型
6. [x] `AdminNodeTrafficView.vue` + `router.ts` + `AdminLayout` 菜单与标题
7. [x] 空态 / 校验错误 toast；默认 30 天 + 第一节点自动查
8. [x] 更新 `.trellis/spec/frontend/component-guidelines.md`（节点流量页约定）

### C. 验证

9. [x] `mvn -Dtest=AdminStatServerQueryTest,PanelApiActionAliasesTest test`（JAVA_HOME=17）
10. [ ] 浏览器：侧栏进入、改节点/日期、隐藏节点、无数据节点、仪表盘排行仍可用（本机无浏览器工具 / 未起管理端）

## Validation

```bash
# API（需 Corretto 17）
mvn -Dtest=AdminStatServerQueryTest,PanelApiActionAliasesTest test

# UI：打开管理后台 /servers/traffic 走一遍主路径
```

## Risky files

- `PanelApiActionCatalog.java`：漏登记则加密路径 404
- `AdminLayout.vue` / `router.ts`：菜单与路由不一致会空白页
- `UserService.recordStatServer`：**不要改**写入日切，否则历史对不齐

## Rollback

删除新 action、新页面与菜单即可；无 schema 变更。
