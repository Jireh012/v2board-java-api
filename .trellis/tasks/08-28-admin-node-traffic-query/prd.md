# 管理后台查询节点流量

## Goal

管理员在后台侧栏打开「节点流量」，选择本站节点和日期范围，查看该节点每日上/下行与区间合计。不再只能看仪表盘今/昨 Top 15。

## Background

- 节点日流量已在 `v2_stat_server`：`server_id` + `server_type` + 当日 `u`/`d`（字节，写入时已乘节点倍率），`record_type=d`。写入：UniProxy 推送 → `JobDispatcher.dispatchStatServer` → `UserService.recordStatServer`（`record_at` 为 **UTC 当日 0 点** unix 秒）。
- 日记录约保留 2 个月（`LogSchedule.resetLog`）。
- 仪表盘 `getServerTodayRank` / `getServerLastRank` 仍为当天/昨天 Top 15（`total` 为 GB），本功能不替换。
- 用户管理「TA 的流量」走 `getStatUser`；`v2_stat_user` 无 `server_id`，无法按节点拆用户。
- 第三方订阅源没有 UniProxy 上报，不在本查询内。

## Requirements

1. 侧栏「服务器」组新增独立页「节点流量」（路径 `/servers/traffic`），不在节点管理行内加按钮。
2. 页内选择一个本站协议节点 + 开始/结束日期后查询。打开页面预填最近 30 天，并默认选中节点列表排序第一项后自动查询。
3. 节点下拉：复用 `GET server/manage/getNodes` 的全部现存节点（含隐藏/下架、含子节点），不含已删除；文案含名称、协议、`#id`。
4. 结果为按天表格：日期、上行、下行、当日合计。单位与「TA 的流量」相同（字节由前端自适应 B/KB/MB/GB）。区间顶部展示所选范围上/下行/总计。
5. 无上报日不补 0 行。区间内无记录时说明：未上报或已超出约 2 个月保留期。
6. 新接口 `GET /api/v1/admin/stat/getStatServer`（需写入 `PanelApiActionCatalog`）。`u`/`d` 返回字节，不在服务端换成 GB。

## Out of scope

- 某节点上有哪些用户（需改 `v2_stat_user`）。
- 按日期看全部节点排行 / 改仪表盘 Top 15。
- 节点管理行内入口、图表、CSV 导出。
- 第三方订阅源流量（已有源上的 `traffic_*`）。
- 改 UniProxy 上报协议；延长 `v2_stat_server` 保留期。
- 修正仪表盘「今日」与 UTC 日切的既有时区差异。

## Acceptance Criteria

- [x] 管理员能从侧栏进入「节点流量」，选节点与日期后看到该节点每日上/下行与区间合计。
- [x] 默认最近 30 天；改日期后可再查。超出保留期的日期无行。
- [x] 隐藏/下架节点可选；已删除节点不出现；第三方订阅源不出现。
- [x] 无记录时有明确空态，不把空当成接口失败。
- [x] 非法参数（缺节点、开始晚于结束、区间过长）返回 `BusinessException`，不 500。
- [x] 仪表盘今/昨排行与用户「TA 的流量」行为不变。
- [x] 前端用 `apiUrl('admin', '/stat/getStatServer')`，catalog 含该 classicRel。

## Technical notes

- 日期按 UTC 自然日对齐 `recordStatServer` 的 `record_at`。日期选择器的 `YYYY-MM-DD` 表示 UTC 日，不是 JVM 默认时区。
- 节点统计已含倍率，表格不显示倍率列。
- PHP 无对等接口；不要求对齐 wyx2685 路径，只遵循现有 admin JSON/`BusinessException` 约定。
- 查询 `vmess` 时同时匹配历史 `v2ray` 行（`getNodes` 只返回 `vmess`）。
