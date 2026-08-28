# Design — 管理后台查询节点流量

## Boundaries

- **API** `v2board-java-api`：只读 `v2_stat_server`；不改上报、队列、清理任务。
- **UI** `v2board-ui`：新管理页 + 侧栏 + 路由；节点列表复用 `fetchNodes()`。
- **v2node**：不改。

## API

`GET /api/v1/admin/stat/getStatServer`

| Query | 规则 |
|-------|------|
| `server_id` | 必填 |
| `server_type` | 必填。`vmess` 与历史 `v2ray` 一并查询（与排行接口名称映射一致）；其余类型精确匹配 |
| `start_date` | `YYYY-MM-DD`，UTC 日（含） |
| `end_date` | `YYYY-MM-DD`，UTC 日（含） |

校验：

- 缺参 / 日期无法解析 → `BusinessException`
- `start_date > end_date` → `BusinessException`
- 跨度 > 62 天 → `BusinessException`（对齐约 2 个月保留期）

查询：`server_id` + `server_type` + `record_type=d` + `record_at` ∈ `[utcStart(start), utcStart(end)]`（两端均为当日 0 点 unix 秒，闭区间），按 `record_at` **降序**。

成功 `data`：

```json
{
  "server_id": 1,
  "server_type": "v2node",
  "server_name": "香港 1",
  "start_date": "2026-07-30",
  "end_date": "2026-08-28",
  "u": 123,
  "d": 456,
  "total": 579,
  "days": [
    { "record_at": 1756339200, "date": "2026-08-28", "u": 10, "d": 20, "total": 30 }
  ]
}
```

`u`/`d`/`total` 均为字节。`server_name` 用现有 `loadAllServers()`；`vmess` 与 `v2ray` 名称映射与排行接口相同。无记录时 `days=[]` 且区间合计为 0，`code=0`。

实现放在 `AdminStatController`，过滤逻辑可抽私有方法便于单测（不引入新表）。

`PanelApiActionCatalog` 增加 `stat/getStatServer`。

## UI

- 路由：`servers/traffic`（与 `servers/external-subscribe` 并列，须写在 `servers` 之前以免被吃掉——当前 `servers` 为精确 path，无通配，顺序不挡子路径）。
- 侧栏「服务器」：`节点流量`，插在「节点管理」后。
- `AdminLayout` 标题 map 增加该 path。
- 页面 `AdminNodeTrafficView.vue`：筛选条（节点 select + 两个 date input + 查询）+ 区间合计 + `data-table`。
- 流量格式复用用户流量页的自适应单位（可抽小函数，禁止复制三份则抽；仅两处可先本地同逻辑）。
- `src/api/admin.ts`（或 `admin/stat.ts` 若已有拆分）新增 `fetchStatServer`。

节点选项：`fetchNodes()` 全量，label：`${name} · ${type} · #${id}`。

## Timezone

写入用 UTC 日。本接口的日期字符串按 UTC 解析，与 `record_at` 对齐。

已知差异：仪表盘今/昨排行用 `ZoneId.systemDefault()`。本页「今天」= UTC 今天。不在本任务改仪表盘。

## Compatibility

- 无 DDL。
- PHP 无此 action；仅 Java 面板。前端必须走 alias，catalog 漏登记会导致加密前缀 404。
