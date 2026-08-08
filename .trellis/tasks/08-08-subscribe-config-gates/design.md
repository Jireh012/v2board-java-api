# Design: subscribe gates + order pricing

## Boundaries

| Layer | Change |
|-------|--------|
| API | `CouponService`；`OrderService.setOrderType` + surplus + `setVipDiscount` + balance apply；`OrderController#save` 完整流水线；`ResetSchedule` DB 默认 |
| UI | `PlanView` / `createOrder`：周期锁定 + `coupon_code`；展示余额抵扣可选（PHP 默认自动用余额） |

## PHP save pipeline（目标顺序）

```
price from plan[period]
if coupon_code → CouponService.use → discount_amount, coupon_id
setVipDiscount(user.discount %)
setOrderType → type + optional surplus adjust
setInvite
if balance > 0 && total > 0 → deduct balance → balance_amount, reduce total
save
```

## CouponService

- Lock row / transactional with order save  
- Checks: show, started/ended, limit_use, limit_plan_ids, limit_period, limit_use_with_user  
- type 1: value cents；type 2: percent of current total_amount  
- Cap discount ≤ total； decrement limit_use when not null  

`limit_plan_ids` / `limit_period` may be JSON arrays in DB — parse like admin commerce.

## VIP discount

```
discount_amount += total * (user.discount / 100)
total -= that increment
```

Apply after coupon (PHP order).

## Balance

- Deduct min(balance, total) via transactional user update  
- `balance_amount` = deducted；`total` reduced  
- `cancel` already refunds `balance_amount` when status 0→2  

## Surplus / plan change / allow_new_period / reset

Unchanged from prior design section (PHP setOrderType + ResetTraffic null fallback + renew period lock).

## UI

- Renew + `allow_new_period=0`: only last period selectable  
- Optional coupon input before `createOrder(planId, period, couponCode?)`  
- Balance: auto-apply like PHP（无单独「是否用余额」开关，除非后续产品要求）

## Risks

- Money rounding：百分比用 long 分，避免浮点误差累积  
- Coupon limit_use 并发：事务 + 行锁或条件更新  
- Surplus + coupon + VIP 顺序必须与 PHP 一致，单测覆盖组合  
