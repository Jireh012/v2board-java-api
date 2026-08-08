# 用户注册页与注册开关

## Goal

用户可在前端自助注册；仅当管理端未开启「停止新用户注册」时显示注册入口与可用注册页，并与现有邀请链接 `/register?code=` 打通。

## Background

- 后端 `POST /api/v1/passport/auth/register`（JSON：`email`/`password`/`invite_code` 等）与 `site.stop_register` 已生效。
- 公开 `GET /api/v1/passport/comm/config` 目前仅返回 `app_name`。
- 前端有登录页与邀请页生成的注册链接，但无 `/register` 路由/页面。
- 本轮不做邮箱验证码 UI、reCAPTCHA。

## Confirmed Facts

| 项 | 现状 |
|----|------|
| 停止注册 | `site.stop_register == 1` 时 register API 拒绝 |
| 强制邀请 | `invite.invite_force == 1` 时必须带 `invite_code` |
| 注册成功 | 返回与登录相同的 auth 数据（可直接建会话） |
| 邀请链接 | `InviteView` 已拼 `/register?code=` |

## Requirements

1. **R1 公开开关**：`passport/comm/config` 增加非敏感字段 `stop_register`、`invite_force`（0/1）。
2. **R2 登录入口**：登录页仅在 `stop_register != 1` 时显示「注册」入口。
3. **R3 注册页**：`/register` 视觉对齐登录页；字段邮箱、密码、确认密码、邀请码（可选或按 `invite_force` 必填）；支持 query `code` 预填邀请码。
4. **R4 关闭态**：若已停止注册仍打开 `/register`，展示不可注册说明并链回登录，不提交。
5. **R5 成功路径**：注册成功写入会话并进入 `/dashboard`。

## Acceptance Criteria

- [ ] AC1：`stop_register=0` 时登录页可见注册入口；`=1` 时不可见。
- [ ] AC2：注册页可成功注册并进入仪表盘（邀请码可选场景）。
- [ ] AC3：`invite_force=1` 时无邀请码前端拦截或后端错误可理解；有 `?code=` 时预填。
- [ ] AC4：`stop_register=1` 访问 `/register` 不出现可提交表单。
- [ ] AC5：公开 config 仍不返回 SMTP / server_token 等敏感项。

## Out of Scope

- 邮箱验证码发送/填写 UI、忘记密码页、reCAPTCHA。
- 管理端注册页。
- 修复其它系统配置未生效项。

## Key Decisions

| 决策 | 选择 |
|------|------|
| MVP 字段 | 邮箱 + 密码 + 确认密码 + 邀请码 |
| 公开配置 | `app_name` + `stop_register` + `invite_force` |
| 关闭注册时入口 | 隐藏按钮；直链 `/register` 显示关闭态 |
| 邮箱验证 | 本轮不做；若后台已开 `email_verify`，注册可能失败（已知限制） |

## Open Questions

（无）
