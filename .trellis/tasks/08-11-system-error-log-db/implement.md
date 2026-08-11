# Implement: 系统错误日志落库

## Checklist

### API (`v2board-java-api`)

1. [x] 新增 `src/main/resources/db/v2_log.sql`（PHP 兼容）
2. [x] `SystemLog` entity + `SystemLogMapper`
3. [x] `SystemLogService`：脱敏、截断、异步 persist、fallback logger 名固定
4. [x] `DbErrorAppender` + programmatic registrar（ERROR threshold；排除 fallback logger）
5. [x] `AdminSystemController#getSystemLog` + `PanelApiActionCatalog`
6. [x] `LogSchedule` 删除 1 个月前 `v2_log`
7. [x] 单测：service 脱敏/截断；controller 分页（MockMvc 或纯 service）；appender 不递归（可选）

### UI (`v2board-ui`)

8. [x] `src/api/admin/systemLog.ts`
9. [x] `AdminSystemLogView.vue` + `router.ts` + `AdminLayout` 导航
10. [ ] 本地联调：造一个 ERROR（或临时接口）→ 列表可见

### Spec

11. [x] 更新 `.trellis/spec/backend/logging-guidelines.md` 与 `error-handling.md`（落库约定）

## Validation

```bash
# api
cd /Users/jireh/Repos/v2board-java-api
# 部署 DDL 后
mvn -q -Dtest=SystemLogServiceTest,AdminSystemLogControllerTest test
mvn -q -DskipTests compile

# ui
cd /Users/jireh/Repos/v2board-ui
npm run build
```

手工：触发未捕获异常 / schedule ERROR → 管理端「系统日志」可见；写库失败时主流程仍 200/正常。

## Risky files

- `logback-spring.xml`：配置错误会导致启动失败或日志环
- `GlobalExceptionHandler`：勿重复双写导致两条相同记录（统一走 logger 即可）

## Before `task.py start`

- [x] prd / design / implement 齐全
- [x] implement.jsonl / check.jsonl 有真实条目
- [ ] 用户批准本规划摘要
