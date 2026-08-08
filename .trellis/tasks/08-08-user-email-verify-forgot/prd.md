# 用户端邮箱验证与找回密码

## Goal

管理端开启「邮箱验证」后，用户注册必须完成邮箱验证码校验；同时提供完整的找回密码流程（发码 → 校验 → 重置），使安全页开关与用户端行为一致。

## Background / Confirmed Facts

- 管理端 `safe.email_verify` 已可保存；`PassportService.register` 在 `email_verify=1` 时强制校验 `email_code`（`PassportService.java`）。
- `POST /api/v1/passport/comm/sendEmailVerify` 已实现；body 支持 `email`、`isforget`（0=注册 / 1=找回）。
- `POST /api/v1/passport/auth/forget` 已实现，始终要求 6 位 `email_code` + 新密码。
- `GET /api/v1/passport/comm/config` 目前仅返回 `app_name` / `stop_register` / `invite_force`，**未**暴露 `email_verify`。
- 用户端有 `/login`、`/register`，无找回密码页；注册页无验证码 UI（`08-08-user-register-page` 明确推迟）。
- SMTP 已改为读 `email.*` 嵌套配置；发信依赖后台邮件配置。

## Requirements

1. 公开配置增加非敏感字段 `email_verify`（0/1），供注册页判断是否展示验证码。
2. 注册页：当 `email_verify=1` 时展示验证码输入 +「发送验证码」（`isforget=0`，60s 冷却）；提交带 `email_code`。
3. 注册页：当 `email_verify=0` 时不展示验证码字段，行为与现网一致。
4. 新增找回密码页（路由 `/forget`）：邮箱 + 验证码（`isforget=1`）+ 新密码/确认密码；成功后跳转登录。
5. 登录页增加「忘记密码」入口指向 `/forget`。
6. 文案与登录/注册页视觉一致（`login.css`）。

## Acceptance Criteria

| ID | Criterion |
|----|-----------|
| AC1 | `GET /api/v1/passport/comm/config` 含 `email_verify`（0/1）；不含 SMTP/密钥等敏感字段 |
| AC2 | `email_verify=1`：注册页可见验证码与发送按钮；未填/错误码注册失败；正确码可注册 |
| AC3 | `email_verify=0`：注册页无验证码字段，注册可不带 `email_code` |
| AC4 | `/forget`：可发码（`isforget=1`）、校验码并重置密码；成功跳转 `/login` |
| AC5 | 登录页有「忘记密码」链接；发送验证码有约 60s 冷却与错误提示 |

## Out of Scope

- 修改管理端「邮箱验证」开关 UI
- 邮箱白名单 / Gmail 限制策略改动（沿用现有后端）
- 将异步发信改为同步（沿用 `MailService.sendEmail`）
- 管理端用户重置密码流程
- reCAPTCHA

## Key Decisions

| 决策 | 选择 |
|------|------|
| 公开配置字段 | 增加 `email_verify` only |
| 注册验证码展示 | 仅 `email_verify=1` |
| 找回密码 | 始终可用（后端 forget 始终要码，与开关无关） |
| 路由 | `/forget` |
| 发码冷却 | 对齐后端 60s（`LAST_SEND_EMAIL_VERIFY_TIMESTAMP`） |

## Risks

- SMTP 未配置时发码失败：UI 需展示后端错误信息。
- 异步发信：接口成功不保证邮件已送达（既有行为）。
