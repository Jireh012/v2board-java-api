# 登录注册 reCAPTCHA 人机验证

## Goal

后台「reCAPTCHA 验证」开关真正生效：开启后**用户登录**与**用户注册**必须完成 Google reCAPTCHA v2，后端校验通过才继续。

## Background / Confirmed Facts

- Admin UI 已有 `safe.recaptcha_enable` / `recaptcha_key`（Secret）/ `recaptcha_site_key`，可持久化；**无运行时消费者**。
- 公开配置 SM4 明文目前不含 reCAPTCHA 字段；**不得**公开 `recaptcha_key`。
- 上游 PHP：仅 `register` 校验 `recaptcha_data`；本任务按产品决策 **B** 扩展到用户登录。
- 用户端：`LoginView` / `RegisterView` 无 widget；`auth.ts` 未传 `recaptcha_data`。
- 登录 API 现为 `application/x-www-form-urlencoded`（`email`/`password`）；注册为 JSON。

## Requirements

1. 公开配置增加 `recaptcha_enable`（0/1）与 `recaptcha_site_key`；永不下发 Secret。
2. 开启且 `site_key` 非空时，用户登录页与注册页展示 reCAPTCHA v2 checkbox。
3. 后端：`POST .../auth/login` 与 `POST .../auth/register` 在开关开启时校验 Google `siteverify`；请求字段名 `recaptcha_data`。
4. 关闭时行为与现网一致（无 UI、不校验）。
5. 管理端登录、找回密码、发邮件验证码：**不**要求 reCAPTCHA（本任务范围外）。
6. Admin 文案保持「注册和登录」——与决策 B 一致。

## Acceptance Criteria

| ID | Criterion |
|----|-----------|
| AC1 | Public config decrypt 含 `recaptcha_enable` + `recaptcha_site_key`，无 `recaptcha_key` |
| AC2 | 关闭时登录/注册无验证码 UI，可不带 token |
| AC3 | 开启后用户登录缺/假 token → 拒绝 |
| AC4 | 开启后用户注册缺/假 token → 拒绝 |
| AC5 | 合法 token 可通过登录与注册（手工或集成测） |
| AC6 | Secret 不出现在公开配置或前端仓库硬编码 |

## Out of Scope

- reCAPTCHA v3 / Enterprise
- 管理端登录验证码
- 找回密码 / `sendEmailVerify`
- 自建验证码

## Key Decisions

| 决策 | 选择 |
|------|------|
| 校验范围 | **B**：用户登录 + 用户注册 |
| 版本 | Google reCAPTCHA **v2** checkbox（与后台文案一致） |
| 请求字段 | `recaptcha_data`（对齐 PHP） |
| 公开字段名 | `recaptcha_enable` + `recaptcha_site_key`（对齐本仓公开配置风格） |
