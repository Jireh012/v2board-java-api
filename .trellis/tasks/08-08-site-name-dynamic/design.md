# Design: 站点名称动态渲染

## Boundaries

| 层 | 职责 |
|----|------|
| Backend | 公开只读配置；从 `ConfigService.getAppName()` 取值 |
| Frontend | 启动拉取、缓存、全站绑定品牌文案与 `document.title` |

## API Contract

`GET /api/v1/passport/comm/config`

- Auth: 无
- Response `data`（snake_case，与全局 Jackson 一致）:

```json
{ "app_name": "V2Board" }
```

- 实现：扩展现有 `CommController`，注入 `ConfigService`，调用 `getAppName()`。
- 不复用 admin `config/fetch`（会泄露完整配置分组）。

## Frontend Shape

1. `src/api/site.ts`：`fetchPublicSiteConfig()`，`request(..., { auth: false })`。
2. `src/siteBrand.ts`（或轻量 composable）：
   - `appName` ref
   - `loadSiteBrand()`：fetch → 写 localStorage key（如 `v2board_app_name`）→ 更新 `document.title`
   - 同步读缓存作为首屏初始值，避免闪「V2Board」过久
3. `main.ts`：挂载前或挂载后立刻 `loadSiteBrand()`（不阻塞 mount；有缓存则首帧即正确）。
4. 替换点：
   - `App.vue` brand-name / sidebar-brand
   - `AdminLayout.vue` brand-name / sidebar-footer（`{appName} Admin`）
   - `LoginView.vue` / `AdminLoginView.vue` login-brand-name
   - `InviteView.vue` 消费文案插值
   - `index.html` 可保留占位；运行时覆盖 title

## Compatibility

- 与 PHP 完整 guest config 不强制字段对齐；仅保证本前端可用。
- 管理端改名后无需重启后端；下次前端拉取即更新（刷新或重新进入即可）。

## Risks

| 风险 | 缓解 |
|------|------|
| 公开接口被扫 | 仅返回非敏感品牌字段 |
| 首屏闪旧名 | localStorage 同步预填 |
| 双仓改动 | 后端先可独立合并；前端依赖新接口 |

## Rollback

- 删前端消费与 API 调用即可回退硬编码；或后端下线该 GET（前端走回退名）。
