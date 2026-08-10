# Quantumult X / Loon 完整 ACL4SSR

## Goal

QX / Loon 订阅从「纯节点列表」升级为带策略组与本地规则的完整配置，语义对齐 ACL4SSR；客户端无需再拉远程规则。

## Parent

`08-10-clash-acl4ssr-rules`（顺序 4）

## Background（仓库事实）

- `QuantumultXBuilder` / `LoonBuilder` 当前只拼节点行，无 policy / filter / rule 段。
- 需新增默认模板资源 + Builder 合并节点与策略组（类似 Surge）。

## Requirements

- 提供 `default.quantumultx.conf`（或项目约定扩展名）与 `default.loon.conf` 种子。
- 输出含完整策略组语义与本地规则（DOMAIN/GEOIP 等内联或客户端内置能力；无远程 filter_url 依赖）。
- 地区组按节点名过滤。
- 与管理端对接：`resolve(format)`；同步/保存经同一 SanitizePipeline。


## Acceptance Criteria

| ID | Criterion |
|----|-----------|
| AC1 | QX/Loon 配置可见完整策略组语义 |
| AC2 | 无远程规则 URL 依赖 |
| AC3 | 地区过滤可用 |
| AC4 | Builder 单测通过 |
