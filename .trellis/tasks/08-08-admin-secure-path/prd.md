# 管理后台路径 secure_path

## Goal

管理端「后台路径」生效：浏览器入口变为 `/{secure_path}`（及登录页），不再固定暴露 `/admin`；API 仍为 `/api/v1/admin/**`。

## Background / Confirmed Facts

- `safe.secure_path` 可保存；无运行时消费者。
- UI 路由/导航大量硬编码 `/admin`、`/admin/login`。
- 公开配置已 SM4 加密，可安全增加非密钥字段 `secure_path`（仍属路径混淆，非强机密）。
- 用户确认：只改前端入口；后端 API 路径不改。

## Requirements

1. 公开配置增加 `secure_path`（规范化后的路径段；空/无效时前端按 `admin`）。
2. `ConfigService.getSecurePath()`：trim；空 → `""` 或 `"admin"`（见决策）；非法字符不返回给客户端（回落默认）。
3. 保存 `safe.secure_path` 时校验：空允许；非空则 ≥8 且 `^[A-Za-z0-9]+$`，且不在保留段列表。
4. 前端：`adminBasePath` + `adminUrl(sub)`；路由、`AdminLayout` 导航、守卫、用户壳「管理后台」链接全部使用动态前缀。
5. 自定义路径启用后，旧 `/admin` **不**再进入管理端（可落到用户壳或 404；推荐不匹配管理路由）。
6. 修改路径后需用新 URL 访问（保存成功提示可选）。

## Acceptance Criteria

| ID | Criterion |
|----|-----------|
| AC1 | Public config decrypt 含 `secure_path` |
| AC2 | 设为 `admin888` → `/admin888`、`/admin888/login` 可用；`/admin` 不进管理端 |
| AC3 | 空路径 → 行为与现网 `/admin` 一致 |
| AC4 | 保存非法值（过短/符号/保留字）被拒绝 |
| AC5 | 管理端内链（侧栏、跳转订单等）仍落在同一前缀下 |
| AC6 | API 调用仍为 `/api/v1/admin/...` |

## Out of Scope

- 改写后端 `/api/v1/admin` 前缀
- Nginx/网关层隐藏
- 多站点多后台路径

## Key Decisions

| 决策 | 选择 |
|------|------|
| 空值含义 | 默认前缀 `admin` |
| API | 保持 `/api/v1/admin/**` |
| 旧 `/admin` | 自定义路径时不再挂载管理路由 |
| 保留段 | `login,register,forget,dashboard,plan,order,server,invite,ticket,traffic,knowledge,profile,api,admin`（当自定义且等于保留字时拒绝；默认 `admin` 仅作空值回落） |

注：空 → `admin` 时允许前缀为 `admin`；若显式填写保留字（含 `admin` 以外）拒绝。显式填 `admin` 允许（等同默认）。

## Risks

- 硬编码 `/admin` 遗漏导致死链 — 全仓检索替换为 helper。
- `main.ts` 需在挂载路由前 `await loadSiteBrand()`，避免首屏用错前缀。
