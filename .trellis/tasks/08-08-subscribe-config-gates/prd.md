# 订阅配置门禁与下单计价补全

## Goal

1. 管理端「订阅」四项配置真正生效：`plan_change_enable` / `reset_traffic_method` / `surplus_enable` / `allow_new_period`。  
2. 用户下单对齐 PHP：优惠券、VIP 折扣（`user.discount`）、余额抵扣。

## Background / Confirmed Facts

- 换购/差价/全局重置/新周期：见前序结论；`OrderController#save` 仍简化。  
- PHP `OrderController::save` 顺序：计价 → 优惠券 → `setVipDiscount` → `setOrderType`（含 surplus）→ `setInvite` → 余额抵扣 → 保存。  
- 本仓：有 `Coupon` 模型与管理端 CRUD；**无**用户侧 `CouponService.use`；下单不传 `coupon_code`；无 VIP/余额抵扣；`OrderService.cancel` 需确认退回余额。  
- UI：`PlanView` / `createOrder` 无优惠券字段。

## Requirements

### A. 订阅配置门禁

1. type=3 时 `plan_change_enable=0` → 拒绝。  
2. type=3 且 `surplus_enable=1` → 完整 PHP `getSurplusValue`，调整金额 / `surplus_order_ids`。  
3. `ResetSchedule`：plan 方法 null → DB `subscribe.reset_traffic_method`。  
4. `allow_new_period=0`：续费同套餐周期锁定（UI + 后端）。

### B. 下单计价补全

5. 可选 `coupon_code`：校验 show/时间/限次/限套餐/限周期/每人限次；type1 固定分 / type2 百分比；写入 `coupon_id` + `discount_amount`；扣减 `limit_use`。  
6. VIP：`user.discount` 百分比叠加进 `discount_amount` 后扣减 `total_amount`（对齐 PHP `setVipDiscount`）。  
7. 余额：有余额且应付 >0 时扣余额 → `balance_amount`；可扣至 0 元单；取消待支付订单退回 `balance_amount`。  
8. UI：下单流程可填优惠券（套餐页或订单确认页择一，推荐下单前输入）。

## Acceptance Criteria

| ID | Criterion |
|----|-----------|
| AC1–AC6 | 同前：换购/差价/重置默认/新周期 |
| AC7 | 有效优惠券降低应付；无效券拒绝 |
| AC8 | 有 `user.discount` 时应付按比例减少 |
| AC9 | 有余额时下单扣余额；取消待支付订单余额退回 |
| AC10 | 余额+券+VIP 组合后 `total_amount>=0`，可出现 0 元单 |

## Key Decisions

| 决策 | 选择 |
|------|------|
| 差价补足 | 完整 PHP `getSurplusValue` |
| 新周期 | UI + 后端强制 |
| 优惠券/VIP/余额 | **纳入本任务**（对齐 PHP save 顺序） |

## Out of Scope

- 支付手续费 `handling_amount` 改动  
- 邀请佣金逻辑大改（沿用现有 `setInvite`）  
- 礼品卡兑换（独立能力）  
