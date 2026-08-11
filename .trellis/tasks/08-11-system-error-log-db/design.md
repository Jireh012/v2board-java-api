# Design: 系统错误日志落库

## Architecture

```
SLF4J ERROR+ ──► Logback DbErrorAppender ──► SystemLogService.persistAsync
GlobalExceptionHandler (兜底) ──► SystemLogService (可与 appender 去重：仅 appender 或 handler 标 once)
Admin GET getSystemLog ──► LogMapper (v2_log)
LogSchedule ──► delete created_at < now-30d
UI AdminSystemLogView ──► admin system/getSystemLog
```

推荐以 **Logback Appender（threshold ERROR）** 为统一入口，覆盖 schedule / queue / handler 的 `log.error`，避免每个 catch 手写 insert。`GlobalExceptionHandler` 继续 `log.error(...)` 即可入库。

## Components (API)

| 组件 | 职责 |
|------|------|
| `db/v2_log.sql` | PHP 兼容 DDL，`CREATE TABLE IF NOT EXISTS` |
| `model.Log` / `LogMapper` | MyBatis-Plus → `v2_log`（类名避免与 `java.util.Logging` 混淆可用 `SystemLog` + `@TableName("v2_log")`） |
| `SystemLogService` | 组装行、脱敏、截断、异步写；写失败 → `LoggerFactory.getLogger("system-log-fallback").error`（该 logger 不挂 Db appender） |
| `DbErrorAppender` | Logback；过滤自身/fallback logger；从 `RequestContextHolder` 取请求元数据 |
| `AdminSystemController#getSystemLog` | `current`/`pageSize`（或 `page_size`）、`level`；返回 `{ list, total, current, pageSize }`（与现有 failedJobs 一致，外层 `ApiResponse`） |
| `LogSchedule` | 增删 `v2_log` 一月前 |
| `PanelApiActionCatalog` | 登记 `system/getSystemLog` |

## Data contract (`v2_log`)

| 列 | 说明 |
|----|------|
| title | 日志 message（截断 ~2k） |
| level | `ERROR` / `FATAL` 等 |
| host | 请求 host 或本机 hostname |
| uri | 路径；非 HTTP → `-` |
| method | HTTP method；非 HTTP → `SCHEDULE` |
| ip | 客户端 IP；无则空 |
| data | 可选脱敏后 query/body JSON；过大省略 |
| context | JSON：logger、exception class/message、截断 stack（~8k） |
| created_at / updated_at | Unix 秒 |

## UI (`v2board-ui`)

- 路由 `/system-log`（或 `/logs`），侧栏靠近「队列监控」
- `src/api/admin/systemLog.ts`：`fetchSystemLogs`
- `AdminSystemLogView.vue`：表格（时间、level、method、uri、title、ip）+ level 下拉 + 分页 + 详情弹层（context/data）
- 样式复用 admin-page / data-table / card

## Trade-offs

| 选项 | 选择 | 原因 |
|------|------|------|
| Appender vs 仅 Handler | Appender | 覆盖非 HTTP ERROR |
| 同步 insert | 异步（默认 executor / `@Async` 或单线程队列） | 不拖慢请求 |
| 响应形状对齐 PHP 裸 `{data,total}` | 对齐本项目 `ApiResponse` + list/total | 与队列页一致 |
| 手动清空 | 不做 | PHP getSystemLog 无此接口；定时清理足够 |

## Compatibility / ops

- 共享 MySQL 已有 `v2_log`：勿改列类型；Java 只追加写。
- 回滚：去掉 appender + controller/UI；表可保留。
- 风险：ERROR 风暴灌表 → 截断 + 异步 + 一月清理；必要时后续加采样（本 MVP 不做）。

## Rollback

1. `logback-spring.xml` 去掉 Db appender  
2. 下线 UI 路由与 action 目录项  
3. 表数据可保留或手工 truncate  
