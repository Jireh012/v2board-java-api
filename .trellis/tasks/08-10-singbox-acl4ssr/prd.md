# Sing-box 完整 ACL4SSR

## Goal

Sing-box 多 selector + 本地 route；**无任何远程 rule_set**；可接管理端自定义模板。

## Parent

`08-10-clash-acl4ssr-rules`（顺序 3）

## Requirements

- 扩展 `default.sing-box.json`（及 old）策略组语义。
- route 使用本地 geosite/geoip 能力或内联 DOMAIN；禁止 remote URL。
- `SingboxBuilder` 支持地区过滤；读 RuleTemplate resolve。

## Acceptance Criteria

| ID | Criterion |
|----|-----------|
| AC1 | 完整策略组语义 |
| AC2 | 产物无 remote rule_set URL |
| AC3 | 地区过滤可用 |
| AC4 | Builder 单测通过 |
