# 用户端安全模式访问守卫

## Goal

管理端开启「安全模式」后，未登录用户无法浏览用户中心业务页，只能访问登录相关公开页，与开关文案一致。

## Background / Confirmed Facts

- `safe.safe_mode_enable` 可在管理端保存；`ConfigService` 有默认值，但无运行时消费者。
- 用户端 `router.beforeEach` 仅守卫 `/admin/**`；业务页（`/dashboard` 等）未登录可进壳层。
- `/api/v1/user/**` 已有 JWT 拦截，数据侧相对安全；缺口主要在前端路由与壳层展示。
- 当前无独立营销主页；`/` 重定向到 `/dashboard`。
- 公开配置已有 `app_name` / `stop_register` / `invite_force` / `email_verify`（邮箱验证任务）。

## Requirements

1. 公开配置增加非敏感字段 `safe_mode_enable`（0/1）。
2. 前端读取该标志；开启且未登录时，除公开路由外一律跳转 `/login`（可带 `redirect` 回跳）。
3. 公开路由：`/login`、`/register`、`/forget`（及必要的路由重定向过程）。
4. `/admin/**` 逻辑不变（既有管理员守卫）。
5. 关闭安全模式时，行为与现网一致（业务页可不登录进入壳层；接口仍要 JWT）。

## Acceptance Criteria

| ID | Criterion |
|----|-----------|
| AC1 | `GET /passport/comm/config` 含 `safe_mode_enable`；无敏感字段泄漏 |
| AC2 | `safe_mode_enable=1` 且未登录：访问 `/dashboard`（及任意业务路径）→ `/login` |
| AC3 | 同上：`/login` `/register` `/forget` 可正常打开 |
| AC4 | `safe_mode_enable=1` 且已登录：业务页可访问 |
| AC5 | `safe_mode_enable=0`：未登录访问业务页不被本守卫拦截 |

## Out of Scope

- 独立营销落地页
- 后端为「页面」再加一层 HTML 守卫（API 已有 JWT）
- 修改管理端开关文案/UI
- 强制全站 HTTPS（`force_https`）

## Key Decisions

| 决策 | 选择 |
|------|------|
| 「主页」含义 | 无独立主页；公开页 = 登录/注册/找回 |
| 守卫位置 | 前端 `router.beforeEach` + `siteBrand.safeMode` |
| 登录后回跳 | 支持 `?redirect=`（可选，推荐） |

## Risks

- 与进行中的邮箱验证任务同时改 `CommController` / `siteBrand`：合并时注意字段并存。
