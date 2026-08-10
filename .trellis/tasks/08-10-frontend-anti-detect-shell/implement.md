# Implement: 前端抗探测壳

## Checklist

### R1 / R2（已完成）

1. [x] Bootstrap / decoy 路由 / adminSeg / 裸布局 / Vite hash / spec

### R3 假官网

9. [x] 重写 `DecoyView.vue` 为中性云/科技单页假官网。
10. [x] 无 `siteBrand` import、无登录 CTA、无 VPN 话术；标题用缓存或「苍穹云」。
11. [x] 更新 `site-brand.md` decoy 场景。
12. [ ] 浏览器点验：`/` 与 `/random-scan` 同页、无 `/config`；`/login` 仍可用。

## Validation

```bash
cd /Users/jireh/Repos/v2board-ui
npm run dev
# 隐私窗口：/ 与 /random-scan → 假官网；Network 无 /config
# /login → 可出现 /config
```

## Risky files

- `src/views/DecoyView.vue`（主改）
- `.trellis/spec/frontend/site-brand.md`
