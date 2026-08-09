# Implement: Admin dashboard

## Checklist

1. [x] UI: `npm i echarts` in `v2board-ui`
2. [x] UI: rewrite `AdminDashboardView.vue` — quick links, metrics, ECharts trend, four rank charts
3. [x] UI: money/GB formatters; loading / empty / error; dispose on unmount
4. [x] API: `v2ray` + `vmess` name map alias in `loadAllServers`
5. [x] Spec: `admin-stat.md` + UI component guideline note
6. [x] Validate: `vue-tsc` (only pre-existing AdminOrdersView errors)

## Validation commands

```bash
cd ../v2board-ui && npx vue-tsc --noEmit 2>&1 | head -40
```

Manual: admin login → dashboard KPIs + charts; quick links; GB tooltips.

## Rollback

Revert UI + `echarts` dep; revert `AdminStatController` alias if needed.
