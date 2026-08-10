# Design: 前端抗探测壳

## Boundaries

| In | Out |
|----|-----|
| `v2board-ui` bootstrap、路由、假落地视图、Vite 生产构建 | 改 `/config` 路径、后端 API、nginx 按路径分流静态 HTML |
| UI Trellis：`site-brand.md` / 必要时 `panel-api-sm4.md` 补充「延后加载」 | 重型混淆、改 localStorage key |

## Architecture

```text
index.html → main.ts
  → createApp + router.mount  (不再 await loadSiteBrand)
  → beforeEach:
       decoy path + !auth  → DecoyView, skip config
       auth/public/admin   → await ensureSiteBrand() then continue
```

### Decoy paths

- `/` when `!localStorage.auth_data`
- Catch-all unmatched routes when `!auth_data`
- Logged-in `/` → `redirect: /dashboard` (existing product home)

### Brand loading

- Replace “boot await” with `ensureSiteBrand()`（可复用现有 `loadSiteBrand` 单飞 Promise）。
- Call sites: router `beforeEach` for non-decoy；login/register/forget 可保留自调用（幂等）。
- DecoyView: **不得** import/call `fetchPublicSiteConfig` / `loadSiteBrand`。
- Title on decoy: cached `appName` or neutral `Panel`（已有 `NEUTRAL_TITLE`）。

### Admin routes (secure_path)

Problem: `path: \`/${adminBasePath.value}/...\`` is fixed at router module evaluate time.

Approach (MVP):

1. Register admin tree under a **param segment**, e.g. `/:adminSeg/login` and `/:adminSeg` + children.
2. `beforeEnter` / `beforeEach`: `await ensureSiteBrand()`；若 `adminSeg !== adminBasePath` → treat as decoy（未登录）或 404 壳；匹配则放行。
3. `adminUrl()` / `isAdminUiPath()` 仍以 `adminBasePath` 为准；导航生成不受影响。

Avoid requiring prior localStorage for first admin visit.

### Decoy UI（R3 假官网）

- 仍为单一 `DecoyView.vue`（`/` 与 catch-all 共用）；**禁止** `siteBrand` / `/config`。
- 结构（单页锚点即可）：
  1. 顶栏：品牌名（缓存或「苍穹云」）
  2. 全幅英雄区：品牌为第一视觉 + 一句云/科技价值主张 + 次要说明（无 CTA 按钮）
  3. 方案区：2–3 项能力（计算/协同/安全类中性表述，非卡片堆叠炫技）
  4. 页脚：占位联系邮箱（`mailto:` 可指向虚构地址）与版权年
- 视觉：自有 CSS 变量；避免紫渐变/奶油衬线/报纸栏；轻量 2–3 处入场动效；英雄区全幅氛围（渐变或柔和几何），非面板仪表盘。
- 无用户壳：`meta.decoy` + 裸 `RouterView`（已有）。
- 文案禁用：VPN、代理、机场、订阅、节点、翻墙、V2Board。

### Build fingerprint

`vite.config.ts` production:

- `build.sourcemap: false`
- `build.rollupOptions.output`: `entryFileNames` / `chunkFileNames` / `assetFileNames` → `assets/[hash].js` 等（保留扩展名）
- Do not add obfuscator plugins

## Data flow

1. Scanner `GET /` → HTML + JS；Vue 渲染 Decoy；无 `/config`。
2. User `GET /login` → `ensureSiteBrand` → `/config` SM4 → LoginView。
3. Admin `GET /{secure_path}/login` → brand load → segment check → AdminLoginView。

## Compatibility

- Invite links to `/register?code=` unchanged.
- Existing bookmarks to `/login` unchanged.
- Cached admin path still speeds segment check；无缓存时以 `/config` 返回值为准。
- `safe_mode_enable` 语义本任务不改。

## Trade-offs

| Choice | Benefit | Cost |
|--------|---------|------|
| SPA 内假落地 | 无 nginx/运维分叉 | JS 仍下载 |
| 延后 `/config` | 根扫无配置信封 | bootstrap/路由更复杂 |
| 动态 admin 段 | 无缓存可进后台 | 需仔细防段碰撞（与 `/login` 等静态路径） |

## Rollback

- Revert UI commit：恢复 boot `await loadSiteBrand`、`/` → dashboard、静态 admin path。
- 镜像回滚 `web:latest` 上一版本即可，无 DB 迁移。
