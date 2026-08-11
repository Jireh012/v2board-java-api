# Design: 第三方订阅自动同步可配置

## Config shape (`subscribe` section)

| Key | Type | Default | Notes |
|-----|------|---------|-------|
| `external_sync_enable` | 0/1 | 见下 | 独立开关 |
| `external_sync_mode` | `interval` \| `cron` | `interval` | |
| `external_sync_interval_value` | int ≥ 1 | `30` | |
| `external_sync_interval_unit` | `minute` \| `hour` \| `day` | `minute` | |
| `external_sync_cron` | string | `0 */30 * * * *` | Spring 6-field cron |

**Default enable derivation (first boot / no DB keys):**

- 若 `v2board.external-subscribe.cron` 为 `-` / 空白 → `external_sync_enable=0`
- 否则 → `enable=1`；若 cron 非默认，可把 `mode=cron` 且 `external_sync_cron`=该值，便于从 env 平滑迁移

**优先级：** DB（经 ConfigService merge）覆盖 defaults；env/yml 只影响 defaults / 迁移种子，不在每次 tick 重读覆盖 DB。

## Scheduling

```
ConfigService.save(subscribe) 
  → validateExternalSync(...)
  → persist
  → ExternalSubscribeSyncScheduler.rescheduleFromConfig()

ExternalSubscribeSyncScheduler
  - holds ScheduledFuture
  - cancel previous
  - if !enable: return
  - if interval: PeriodicTrigger(Duration.of(...)) fixedRate=false (fixedDelay after completion preferred to avoid overlap; syncAll already has lock)
  - if cron: CronTrigger(cron)
  - task → syncService.syncAll()
```

Remove `@Scheduled` from `ExternalSubscribeSchedule`（类可改为委托 scheduler 的 runnable，或删除并由 scheduler 直接调 service）。

`ApplicationReadyEvent`：初次 `rescheduleFromConfig()`。

Overlap：继续依赖 `ExternalSubscribeSyncService` 进程内锁；fixedDelay 降低堆积。

## Validation

| Mode | Rule |
|------|------|
| enable=0 | 其它字段可不校验严格（仍可保存草稿） |
| interval | value ≥ 1；unit ∈ {minute,hour,day}；建议上限 day≤30 / hour≤168 / minute≤10080 防误配（可在实现取合理上限） |
| cron | `CronExpression.parse` 成功；禁止 `-` 作为「关闭」（关闭只用 enable） |

## API / UI

- 无新 endpoint：走现有 `admin/config` fetch/save `subscribe` 组。
- `AdminSystemConfigView` 订阅页增加一块「第三方订阅自动同步」。
- Types：`config.ts` / 本地 interface 补字段。

## Trade-offs

| 选项 | 选择 | 原因 |
|------|------|------|
| fixedRate vs fixedDelay | fixedDelay | 长同步不叠触发 |
| 6-field cron | 保持 | 对齐现网 Spring / 文档 |
| 独立 section vs subscribe | subscribe | 与订阅域一致、少一个 tab |

## Rollback

- 恢复 `@Scheduled` + env cron；忽略新 keys（harmless）。
