# Implement: 第三方订阅自动同步可配置

## Checklist

### API

1. [x] `ConfigService.buildDefaults` 增加 `subscribe.external_sync_*`；从 `ExternalSubscribeProperties.cron` 推导 enable/mode 种子
2. [x] `ConfigService.save`（或 subscribe 校验钩子）校验 external_sync_*
3. [x] 新增 `ExternalSubscribeSyncScheduler`（TaskScheduler + cancel/reschedule）；`ApplicationReadyEvent` + save 后调用
4. [x] 去掉 `ExternalSubscribeSchedule` 的 `@Scheduled`（类已删除，由 scheduler 直接调 service）
5. [x] Reader helper：`ConfigService.getExternalSyncSettings()` 或等价
6. [x] 单测：校验非法 cron/interval；scheduler enable=0 不 schedule（mock TaskScheduler）
7. [x] 更新 `.trellis/spec/backend/external-subscribe.md`、`system-config.md`

### UI

8. [x] `AdminSystemConfigView` 订阅分组表单项（开关、模式、间隔值+单位、cron）
9. [x] 配置类型定义（若有）补齐字段

## Validation

```bash
cd /Users/jireh/Repos/v2board-java-api
mvn -q -Dtest=ExternalSubscribeSyncConfigTest,ExternalSubscribeSyncSchedulerTest test
mvn -q -DskipTests compile

cd /Users/jireh/Repos/v2board-ui
npm run build
```

手工：开开关 → 设 1 分钟间隔 → 观察日志 `ExternalSubscribeSchedule`/`syncAll`；改 cron；关开关后不再触发。

## Before start

- [x] prd / design / implement
- [x] jsonl curated
- [ ] 用户批准规划摘要
