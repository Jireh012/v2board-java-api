# 登录密码错误锁定

## Goal

后台「密码错误限制」在用户登录真正生效：按邮箱计数，达到上限后在锁定窗口内拒绝继续尝试。

## Background / Confirmed Facts

- Config keys already persist: `safe.password_limit_enable`（默认 1）、`password_limit_count`（5）、`password_limit_expire`（分钟，60）。
- `CacheKeyUtil` 已允许 `PASSWORD_ERROR_LIMIT`；**登录路径未读写**。
- 上游 PHP `AuthController::login`：按 **email** 读/写 `PASSWORD_ERROR_LIMIT_{email}`；达上限先拒；密码错误再 `count+1` 并 TTL=`expire*60` 秒；用户不存在不计数；成功登录不清零（靠 TTL）。
- Admin UI 文案误写「同 IP」；管理端 `/api/v1/admin/login` 不在 PHP 该逻辑内。

## Requirements

1. `POST /api/v1/passport/auth/login`：`password_limit_enable=1` 时按邮箱执行锁定逻辑（对齐 PHP）。
2. 超限错误文案对齐 PHP：`There are too many password errors, please try again after :minute minutes.`（可用配置分钟数替换）。
3. Admin 文案改为按邮箱锁定（非 IP）。
4. 管理端登录、找回密码：**不**纳入本任务。

## Acceptance Criteria

| ID | Criterion |
|----|-----------|
| AC1 | enable=0 时登录行为与现网一致（无锁定） |
| AC2 | enable=1：连续错误达 `count` 后，该邮箱在 `expire` 分钟内登录失败（含正确密码） |
| AC3 | 邮箱不存在不增加计数 |
| AC4 | Redis key：`PASSWORD_ERROR_LIMIT_{email}`，TTL=`expire` 分钟 |
| AC5 | Admin 文案不再写「同 IP」 |

## Out of Scope

- 管理端登录锁定
- 按 IP 锁定（与 PHP 不一致）
- 成功登录主动清零计数

## Key Decisions

| 决策 | 选择 |
|------|------|
| 计数维度 | 邮箱（PHP） |
| 管理端登录 | 不做 |
| 成功清零 | 不做（靠 TTL） |
