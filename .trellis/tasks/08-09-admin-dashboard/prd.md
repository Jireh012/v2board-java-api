# 完善管理端仪表盘

## Goal

将管理端仪表盘从占位页升级为可运维的数据总览：快捷入口、核心指标、31 日趋势、节点/用户流量排行，行为与字段对齐 [wyx2685/v2board](https://github.com/wyx2685/v2board) 管理端仪表盘 + 既有 Java `AdminStatController`。

## Confirmed (repo evidence)

- UI `AdminDashboardView.vue` 仅为「快速开始 / 界面说明」占位。
- UI `src/api/admin.ts` 已封装：`fetchStatOverride` / `fetchStatOrder` / 节点与用户今日·昨日 Rank；**视图未调用**。
- API `AdminStatController` 已提供与 PHP `StatController` 同名的主要端点；`getOverride` 字段与 PHP 一致。
- 趋势数据来自 `v2_stat`；流量排行来自 `v2_stat_server` / `v2_stat_user`。
- UI 当前无图表依赖；**已选定 ECharts**。
- PHP 上游：`.trellis/spec/backend/php-upstream.md`。

## Requirements

1. **快捷入口**：系统设置、订单管理、订阅管理、用户管理 → 现有 admin 路由（`adminUrl`）。
2. **核心指标**：`getOverride` — 在线人数、今日收入、实时注册；辅：本月收入、上月收入、上月佣金支出、本月新增用户。金额：分→元，两位小数 + `CNY`。
3. **31 日趋势**：ECharts 折线，系列来自 `getOrder` 的 `type` 字段；图例可开关；空数据友好空态。
4. **流量排行**：今日/昨日节点、今日/昨日用户（水平条）；tooltip 流量约 2 位小数 + `GB`；用户轴为 email。
5. **加载与错误**：并行拉数；分块失败不拖垮整页。
6. **API**：以现有契约为主；验收发现与 PHP 缺口时再最小修补（如 `v2ray`/`vmess` 名称映射）。

## Decisions

| 项 | 结论 |
|----|------|
| 图表库 | **ECharts**（用户选定 A） |
| 范围 | 管理端仪表盘；不含用户端 dashboard / 告警 / 实时推送 |

## Out of scope

- WebSocket、自定义日期报表、告警、节点健康汇总、用户端 `/dashboard`、重写日聚合任务。

## Acceptance Criteria

- [ ] 不再是双说明卡片占位；结构：快捷入口 → 指标 → 趋势 → 双排行。
- [ ] 指标来自 `getOverride`，金额/人数展示正确。
- [ ] 趋势与四块排行接线；流量 tooltip 可读。
- [ ] 快捷入口可达对应管理页。
- [ ] 与 PHP 端点语义对齐；有意差异记入 design/spec。
