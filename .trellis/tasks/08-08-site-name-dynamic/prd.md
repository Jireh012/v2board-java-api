# 站点名称动态渲染

## Goal

管理端保存的 `site.app_name` 驱动全站品牌文案：用户端/管理端顶栏与侧栏、登录页、邀请页、`document.title` / `index.html` 标题，均动态展示，不再写死「谜之站点」。

## Background

- 后端已有 `ConfigService.getAppName()`，订阅模板与验证码邮件会用；管理端「系统配置 → 站点」可改 `app_name`。
- 前端硬编码位置：`App.vue`、`AdminLayout.vue`、`LoginView.vue`、`AdminLoginView.vue`、`InviteView.vue`、`index.html`。
- 登录页未鉴权；`/api/v1/admin/config/fetch` 不可用。`passport/**` 不经 `ClientAuthInterceptor`，适合挂公开只读接口。

## Confirmed Facts

| 项 | 现状 |
|----|------|
| 配置键 | `site.app_name`（空则后端回退 `V2Board`） |
| 硬编码清单 | 见 Background；另有邀请页「仅用于谜之站点消费使用」 |
| `document.title` | `index.html` 初始为「谜之站点」；仪表盘导入链接会读 `document.title` |

## Requirements

1. **R1 公开站点名 API**：未登录可获取当前 `app_name`；响应不含 SMTP Token、server_token 等敏感字段。
2. **R2 前端统一消费**：应用启动时拉取并缓存站点名；所有硬编码「谜之站点」品牌位改为渲染该值（含管理端与邀请页文案中的站点名片段）。
3. **R3 文档标题**：运行时将 `document.title` 设为站点名（影响浏览器标签与依赖 title 的导入链接名）。
4. **R4 回退**：请求失败或未返回时，优先用本地缓存的上次成功值；再无则显示 `V2Board`（与后端默认一致），不继续写死「谜之站点」。

## Acceptance Criteria

- [ ] AC1：管理端把站点名称改为非默认值并保存后，未登录打开用户登录页与管理登录页，品牌标题显示新名称。
- [ ] AC2：登录后用户端顶栏/侧栏、管理端顶栏/侧栏页脚显示同一名称。
- [ ] AC3：邀请页「仅用于…消费使用」文案中的站点名与配置一致。
- [ ] AC4：浏览器标签 `document.title` 为配置的站点名。
- [ ] AC5：公开接口在无 Authorization 时可用；响应不含邮件密码、通讯密钥等敏感配置。
- [ ] AC6：公开接口失败时页面仍可渲染（回退缓存或 `V2Board`），不白屏。

## Out of Scope

- LOGO、站点描述、TOS、货币符号、主题、APP 下载等其它站点配置的前端消费。
- 完整对齐 PHP `guest/comm/config` 全量字段（本轮仅品牌所需的 `app_name`）。
- 修复系统配置其它未生效项（邮件嵌套读取、充值奖励等）。

## Key Decisions

| 决策 | 选择 |
|------|------|
| 替换范围 | 所有写死「谜之站点」的品牌展示位 + `document.title` |
| 公开接口挂载 | `GET /api/v1/passport/comm/config`（沿用现有 `CommController`） |
| 下发字段 | MVP 仅 `app_name` |
| 失败回退 | localStorage 上次成功值 → `V2Board` |

## Open Questions

（无）
