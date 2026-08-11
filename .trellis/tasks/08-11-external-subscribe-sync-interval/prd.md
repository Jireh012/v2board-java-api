# 第三方订阅自动同步间隔可配置

## Goal

管理员在系统配置「订阅」中动态控制第三方订阅源自动同步：独立开关 + **interval（分/时/天）** / **cron** 模式切换；保存后重调度生效，手工同步不受影响。

## Background

- 现状：`ExternalSubscribeSchedule` 仅 `@Scheduled` + `EXTERNAL_SUBSCRIBE_CRON` / yml，启动固定。
- 已有 `ThreadPoolTaskScheduler`；配置经 `ConfigService` nested map + Admin 系统配置 UI。

## Confirmed decisions

| 决策 | 选择 |
|------|------|
| 模式 | `interval` ↔ `cron` 可切换 |
| 间隔单位 | minute / hour / day |
| 关闭方式 | **独立开关**（禁用后不注册自动任务） |
| UI 位置 | 系统配置 → 订阅分组 |
| 生效 | 保存后动态重调度，无需重启 |
| 手工同步 | 始终可用 |

## Requirements

1. `subscribe` 配置增加自动同步字段（见 design 键名）：enable、mode、interval_value、interval_unit、cron。
2. 保存时校验：enable 开启时，interval 模式要求正整数 + 合法单位；cron 模式要求合法 6 段 Spring cron（与现网一致）；非法 → `BusinessException`。
3. 调度器读取合并后配置：关闭则取消任务；开启则按模式注册；保存配置后立即 `reschedule`。
4. 无 DB 覆盖时：默认 enable 与现网一致（由 env/yml cron 推导或默认每 30 分钟；dev `cron=-` → enable 默认关）——见 design。
5. 移除/停用固定 `@Scheduled` 对 external subscribe 的绑定，避免双触发。
6. 更新 `external-subscribe.md`、`system-config.md`；Admin UI 表单项与模式切换显隐。

## Out of scope

- 按订阅源单独设置不同周期
- 改动同步业务逻辑（fetch/probe/dedupe）
- 分布式多实例调度锁（维持现有单实例假设）

## Acceptance Criteria

- [ ] UI：开关 + 模式切换；interval 显示数值与单位；cron 显示表达式；保存成功
- [ ] enable=0：自动 `syncAll` 不再触发；手工 sync 仍可用
- [ ] interval：分/时/天按设定周期触发
- [ ] cron：合法表达式触发；非法保存失败
- [ ] 保存后无需重启即可换周期
- [ ] 不会与旧 `@Scheduled` 双跑

## Open questions

（无）
