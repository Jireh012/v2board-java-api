# Admin Stat / Dashboard

> Contracts for `/api/v1/admin/stat/*` powering the admin dashboard (PHP-compatible).

## Upstream

Align with [wyx2685/v2board](https://github.com/wyx2685/v2board) `StatController` — see [php-upstream.md](./php-upstream.md).

## Endpoints

| Path | Role |
|------|------|
| `GET .../getOverride` | KPI: online, incomes (cents), registers, pending ticket/commission, commission payouts |
| `GET .../getOrder` | Last 31 daily rows → `{ type, date, value }[]` |
| `GET .../getServerTodayRank` / `getServerLastRank` | Top servers; `total` in **GB** |
| `GET .../getUserTodayRank` / `getUserLastRank` | Top users; `total` in **GB** (rate-weighted) |
| `GET .../getStatUser` | Per-user daily traffic (admin user detail; not dashboard) |
| `GET .../getStatServer` | Per-node daily traffic for a date range (admin「节点流量」; Java-only) |

### `getStatServer`

Query: `server_id`, `server_type`, `start_date`, `end_date` (`YYYY-MM-DD`, **UTC** calendar days, inclusive). Max span **62** days. `u`/`d`/`total` are **bytes** (not GB). Empty range → `days: []` and zero totals, `code=0`. `vmess` also matches legacy `v2ray` rows. Invalid range → `BusinessException`.

Must be listed in `PanelApiActionCatalog` as `stat/getStatServer`. Name lookup includes child nodes (`parent_id` set); dashboard rank still uses parent-only `loadAllServers()`.

### `getOverride` money fields

`day_income`, `month_income`, `last_month_income`, `commission_month_payout`, `commission_last_month_payout` are **cents**. UI: `/100` → two decimals + `CNY`.

### Server name map

`loadAllServers` must expose both `vmess` and legacy `v2ray` keys to the same Vmess name map (PHP does). Missing alias → empty `server_name` on old `stat_server` rows.

### `getOrder` series types

`注册人数`, `收款金额`, `收款笔数`, `佣金金额(已发放)`, `佣金笔数(已发放)`. Amount series already yuan (`paid_total / 100` server-side).

## UI

`v2board-ui` `AdminDashboardView.vue` + `AdminNodeTrafficView.vue` + `fetchStat*` in `api/admin.ts`; dashboard charts via ECharts.
