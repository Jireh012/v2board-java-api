# Clash / Stash 完整 ACL4SSR

## Goal

`flag=clash|meta|verge|nyanpasu|stash` 订阅获得完整 ACL4SSR 风格策略组与分流；国内可用（无 GitHub rule-providers）。

## Parent

`08-10-clash-acl4ssr-rules`（顺序 1）

## Requirements

同父任务 R1–R3，落地于：

- `rules/default.clash.yaml`
- `ClashMetaBuilder.mergeProxyGroup`：空=全节点；正则=过滤；仅策略引用=不追加
- 主组名 `🚀 节点选择`；规则用 Meta `GEOSITE`/`GEOIP`
- Stash 若共用 Clash 模板则一并覆盖

## Out of Scope

Surge / Sing-box（其他子任务）；管理端编辑；GFW 全文内联

## Acceptance Criteria

| ID | Criterion |
|----|-----------|
| AC1 | YAML 含 🍎/📹/🌍/📲/🐟 等完整组 |
| AC2 | 无 raw.githubusercontent.com rule-providers |
| AC3 | 港节点进港组；无匹配可删空组 |
| AC4 | `ClashMetaBuilderTest` 通过 |
