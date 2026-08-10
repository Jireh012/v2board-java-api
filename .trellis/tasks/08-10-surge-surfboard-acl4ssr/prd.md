# Surge / Surfboard 完整 ACL4SSR

## Goal

Surge / Surfboard 订阅具备与 Clash 语义对齐的策略组与分流；国内可用。

## Parent

`08-10-clash-acl4ssr-rules`（顺序 2；依赖 Clash 子任务锁定的分组表）

## Requirements

- 扩展 `default.surge.conf` / `default.surfboard.conf` 的 `[Proxy Group]` 与 `[Rule]` 指向对应组（现有 DOMAIN 规则改策略目标，而非只指向 `Proxy`）。
- Builder：在仅 `$proxy_group` 全量注入之外，支持地区组按节点名过滤（或等价生成）。
- 不新增必须访问 GitHub 的 RULE-SET；可用已有内联 DOMAIN / 国内 GEOIP MMDB URL（如现有 zhimg）。

## Acceptance Criteria

| ID | Criterion |
|----|-----------|
| AC1 | 配置含完整策略组语义（苹果/油管/媒体/电报/广告/漏网等） |
| AC2 | 无 raw.githubusercontent.com 规则源 |
| AC3 | 地区组按节点名过滤可用 |
| AC4 | 相关 Builder 单测或快照断言通过 |
