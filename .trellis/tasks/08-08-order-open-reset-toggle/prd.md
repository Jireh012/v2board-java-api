# 订单开通清零流量开关

## Goal

将「新购/续费/变更事件 ID」改为语义清晰的开关；开启后，对应类型订单**开通成功时**清零用户已用流量（u/d），对齐 PHP `openEvent(1)`。

## Background / Confirmed Facts

- 配置键仍为 `subscribe.new_order_event_id` / `renew_order_event_id` / `change_order_event_id`（PHP 存 0/1）。
- PHP：`open()` 按 order.type 取对应 event；仅 `1` = `buyByResetTraffic`（u=d=0）；`0` = 无操作。
- 本仓可保存配置，但 `OrderService.open` 未调用。

## Requirements

1. Admin UI：三个 **Toggle**（文案改为「新购/续费/变更时清零已用流量」），绑定原 0/1 字段。  
2. `OrderService.open`：type 1/2/3 开通流程中读取对应配置；值为 1 时 `u=0,d=0`。  
3. type 其它（重置包/充值）不走这三项。  
4. 兼容已存数值：非 0 且视为开启时仅认 `1`（与 PHP 一致）；UI 只写 0/1。

## Acceptance Criteria

| ID | Criterion |
|----|-----------|
| AC1 | 管理端为开关而非数字输入 |
| AC2 | 续费开关开 + type=2 开通 → u/d 为 0 |
| AC3 | 开关关 → 开通不清零（除非其它逻辑如新购/reset_price 自身清零） |
| AC4 | 仍持久化为 `*_event_id` 0/1 |

## Out of Scope

- 其它 event id 值 / Webhook  
- 改 DB 字段名  
