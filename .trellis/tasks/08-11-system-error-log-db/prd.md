# 系统错误日志落库 v2_log

## Goal

运维可在管理后台查看 Java API 的 ERROR 及以上异常/错误记录（落库 `v2_log`），无需只靠文件日志或直连数据库。

## Background

- PHP 上游：`MysqlLoggerHandler` → `v2_log`；`GET system/getSystemLog`；`reset:log` 删除 1 个月前记录。
- Java 现状：`GlobalExceptionHandler` / schedule 仅 SLF4J；`LogSchedule` 未清 `v2_log`；UI 有「队列监控」无系统日志页。

## Confirmed decisions

| 决策 | 选择 |
|------|------|
| 落库级别 | ERROR 及以上 |
| 交付面 | 管理端 API + 页面 |
| 表 | 共用 PHP `v2_log`（缺表则 DDL `IF NOT EXISTS`） |
| 业务异常 | 普通 `BusinessException` / 校验失败不落库 |
| 手动清空 | MVP 不做（依赖定时清理）；与 PHP `getSystemLog` 一致 |

## Requirements

1. ERROR+ 写入 `v2_log`（HTTP 未捕获异常 + 后台 `logger.error` 路径），字段语义兼容 PHP：`title`, `level`, `host`, `uri`, `method`, `ip`, `data`, `context`, `created_at`, `updated_at`。
2. 无 HTTP 上下文时：`uri`/`method` 使用占位（如 `-` / `SCHEDULE`），保证 NOT NULL 列合法。
3. `GET /api/v1/admin/system/getSystemLog`：分页、`created_at` 倒序、可选 `level` 筛选；登记 Panel action 目录。
4. 管理 UI：导航「系统日志」、列表、level 筛选、分页、详情（context）、风格对齐现有 admin 页。
5. `LogSchedule` 删除 1 个月前的 `v2_log`。
6. 写库失败只回落文件日志，不拖垮请求/任务，不因写库失败递归刷库。
7. 请求体/上下文脱敏：密码、token、Authorization、SM4 相关密钥不入库明文。

## Out of scope

- INFO/DEBUG/WARN 全量落库
- 合并 `v2_job_failed` / 登录 / 邮件日志
- 手动清空/删除单条 API（MVP）
- 修改 PHP Horizon

## Acceptance Criteria

- [ ] 触发未捕获 500 后，`v2_log` 出现 ERROR 行且含 uri/method/ip/context 摘要
- [ ] 定时任务 `logger.error`（或等价 ERROR 落库）可在无请求上下文时入库
- [ ] 管理端 `getSystemLog` 分页可用；`level` 筛选生效
- [ ] UI「系统日志」可浏览、筛选、翻页、查看详情
- [ ] `LogSchedule` 清理 ≥1 个月前 `v2_log`
- [ ] 写库异常不影响原响应；提供幂等 DDL

## Open questions

（无）
