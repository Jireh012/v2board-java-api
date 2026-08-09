# 队列监控与异步流量对齐（C3）

## Goal

用 Redis 持久任务队列承载面板后台作业，管理端提供接近 PHP「队列监控」的积压/线程/失败可见性；任务可跨进程重启保留，失败可落库、可重试/删除；每队列并发可配置。不兼容 Laravel Horizon 协议。

## Background

- PHP：[wyx2685/v2board](https://github.com/wyx2685/v2board) Horizon 队列 `order_handle` / `traffic_fetch` / `stat` / `send_email` / `send_telegram`（另有 `send_email_mass`）。
- Java 现状：`AsyncConfig` 进程内池 + `@Scheduled`；`getQueueStats`/`horizon:true` 为假数据；UI 无队列页；`trafficFetch` 在 push 请求线程同步写 Redis。
- 决策：用户选 **C → C3**（完整迁移 + Failed 落库 + 可配并发 + 中文队列名 UI）。

## Requirements

1. **R1 Broker**：Redis List 可靠队列（waiting + processing + reclaim）；payload JSON；队列名对齐 PHP 五条（不含 mass）。
2. **R2 Producers**：订单/流量/统计/邮件/TG 入队点改为 enqueue；去掉对这些路径的 `@Async` 依赖（调度兜底仍可入队）。
3. **R3 Consumers**：应用内嵌 worker；每队列 concurrency 可配置（`application.yml` / 可选系统配置）；优雅停机尽量 ack 或回队。
4. **R4 Failed**：失败落 MySQL 表（含 queue、job type、payload、exception、failed_at）；管理端列表 + 重试 + 删除。
5. **R5 Monitor API**：总览（作业量、失败数、运行状态）+ 每队列 workload（作业量/线程数/占用或活跃时间近似）；`horizon` 不再谎报——用 `queue_workers`（或等价）表示 worker 存活。
6. **R6 Admin UI**：菜单「队列监控」；中文名对齐 PHP：订单队列、邮件队列、Telegram消息队列、统计队列、流量消费队列；展示 Failed。
7. **R7 Push**：节点 push 对 `traffic_fetch`/`stat` 只入队，不在 HTTP 线程做重统计/刷 hash 循环。
8. **R8 Catalog**：新 admin API 记入 `PanelApiActionCatalog`。

## Acceptance Criteria

| ID | Criteria |
|----|----------|
| AC1 | 五队列名存在且 producer/consumer 打通；重启 API 后 waiting 中任务仍可被消费 |
| AC2 | Failed 表有记录；管理端可重试入队或删除 |
| AC3 | 每队列 concurrency 配置生效（改配置重启或热更，文档注明） |
| AC4 | 队列监控页展示中文队列名 + 非假数据；系统状态不显示虚假 `horizon: true` |
| AC5 | UniProxy/V2 push 路径不调用同步 `trafficFetch` 写完整个 map（改为入队） |
| AC6 | 单测：enqueue/fail/retry/stats；关键路径回归（订单开通、push 入队）不破坏 |
| AC7 | `send_email_mass` 不在本轮；文档标明 defer |

## Out of Scope

- Laravel Horizon Redis 键/协议兼容
- 独立于 API 的外部 worker 进程部署形态（可二期）
- `send_email_mass` / 管理端邮件群发产品
- 精确复制 PHP Horizon「这一小时处理量」算法（可用 completed 计数近似，需在 API 说明）

## Key Decisions

| Decision | Choice |
|----------|--------|
| 方案档位 | C3 |
| Broker | Redis Lists + processing + reclaim（非 Streams 首期，降低运维面） |
| Failed 存储 | MySQL 新表（Java 自有 DDL，见 design） |
| 队列集合 | 5 条 PHP 主队列；无 mass |
| UI 文案 | 中文标签对齐 PHP admin |
| 调度兜底 | 保留 `OrderSchedule` / `TrafficSchedule` 等 |

## Open Questions

（无阻塞项）

## Risks

- 入队失败时订单/流量丢失风险 → producer 失败打日志 + 订单扫单兜底；流量依赖节点重试 push。
- Redis 与 API 同机假设 → 多实例时多 consumer 争抢同一 List（正确）；需文档说明勿混用 DB0 前缀冲突。
- 迁移窗口双写风险 → 一次切换，不长期双轨。
