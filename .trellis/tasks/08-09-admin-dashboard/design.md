# Design: Admin dashboard

## Boundaries

| Layer | Responsibility |
|-------|----------------|
| UI `AdminDashboardView.vue` | Layout, parallel fetch, ECharts, formatters, quick links |
| UI `src/api/admin.ts` | Existing `fetchStat*` clients (extend types only if needed) |
| API `AdminStatController` | Keep PHP-compatible contracts; patch only confirmed gaps |

Sibling UI repo: `v2board-ui`. Task artifacts live in API Trellis.

## Contracts (API → UI)

### `GET /api/v1/admin/stat/getOverride`

| Field | Unit / meaning |
|-------|----------------|
| `online_user` | count (traffic `t` within 600s) |
| `day_income` / `month_income` / `last_month_income` | **cents** |
| `day_register_total` / `month_register_total` | count |
| `commission_month_payout` / `commission_last_month_payout` | **cents** |
| `ticket_pending_total` / `commission_pending_total` | count (optional chips; not required for MVP layout) |

UI money: `(cents / 100).toFixed(2) + ' CNY'`. Null/missing → `0.00 CNY` or `0`, never bare `-` unless request failed.

### `GET /api/v1/admin/stat/getOrder`

Array of `{ type, date, value }` (last ~31 days, reversed to chronological). Known `type` strings (PHP/Java):

- `注册人数`
- `收款金额` / `收款笔数`（金额已是元）
- `佣金金额(已发放)` / `佣金笔数(已发放)`

UI charts whatever `type` values appear; do not invent unpaid/refund series unless API adds them later.

### Rank endpoints

- Server: `total` already **GB**; label `server_name`.
- User: `total` already **GB** (rate-weighted); label `email`.
- Tooltip: `Number(total).toFixed(2) + ' GB'`.

## UI composition

```
[Quick links ×4]
[Primary metrics ×3] + [Secondary metrics row]
[ECharts line: getOrder]
[Server today | Server yesterday]
[User today | User yesterday]
```

- Quick links use `RouterLink` + `adminUrl('/config/system'|'/orders'|'/plans'|'/users')`.
- Style: reuse admin tokens (`admin.css` / existing `.card` / `.stat-*` patterns from `AdminServersView`); light theme, no purple glow kits.
- ECharts: dependency `echarts` (+ optional `vue-echarts` **or** imperative `echarts.init` in `onMounted`/`onBeforeUnmount` — prefer imperative or thin wrapper to match repo simplicity).
- Resize: `ResizeObserver` or window resize → `chart.resize()`.
- Fetch: `Promise.allSettled` per section so one 403/500 does not blank all blocks.

## API gap checklist (implement-time verify)

1. `server_type` legacy `v2ray` vs `vmess` name map (PHP loads both keys to same table) — if ranks miss names, alias in `loadAllServers`.
2. Confirm Jackson SNAKE_CASE matches TS interfaces.
3. No change to daily `v2_stat` writers in this task; empty chart is valid when table empty.

## Tradeoffs

| Choice | Why |
|--------|-----|
| ECharts | Matches PHP theme multi-series + legend toggle |
| Reuse existing APIs | Avoid duplicate aggregation logic |
| Format in UI | API already returns cents / GB as PHP does |

## Rollout / rollback

- Ship UI + optional tiny API alias fix.
- Rollback: revert `AdminDashboardView` + remove `echarts` dep; API unchanged if no patch.
