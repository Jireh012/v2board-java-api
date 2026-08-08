# 邀请佣金配置读库生效

## Goal

管理端「邀请 & 佣金」全部读 `invite.*` DB 配置；提现限额按「分」正确比较。

## Requirements

1. `InviteController`：`invite_gen_limit`、`invite_commission`、分销 L1 比例读 ConfigService，非仅 yml。  
2. `CommissionSchedule`：自动审核 / 三级分销 / 关闭提现读 `invite` 分组。  
3. `TicketController#withdraw`：关闭提现、提现方式、最低金额读 DB；限额与 `commission_balance` 均以**分**比较。  
4. `OrderService#setInvite` 已读 invite 分组；补齐 Boolean/String 兼容。  
5. ConfigService 增加必要 getters；简要 spec。

## Acceptance Criteria

| ID | Criterion |
|----|-----------|
| AC1 | 改邀请码上限并保存后立即限制生成 |
| AC2 | 关闭佣金自动审核后 schedule 不再自动 0→1 |
| AC3 | 开启三级分销后按 L1/L2/L3 比例发放 |
| AC4 | 提现限额 100（分）时余额 ≥100 分可提；关闭提现时拒绝提现工单 |

## Out of Scope

- 用户端邀请页大改 UI  
- 佣金手动审核管理端页面  
