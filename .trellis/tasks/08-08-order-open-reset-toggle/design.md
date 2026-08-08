# Design: 订单开通清零流量开关

## Boundaries

| Layer | Change |
|-------|--------|
| Config / DB | Keep keys `new_order_event_id` / `renew_order_event_id` / `change_order_event_id` as 0/1 |
| API | `OrderService.open` after period branch, before persist: if event==1 then `u=d=0` |
| Admin UI | Replace number inputs with Toggle; labels = 清零已用流量 |

## Contract

```
order.type → config key
1 (新购) → subscribe.new_order_event_id
2 (续费) → subscribe.renew_order_event_id
3 (变更) → subscribe.change_order_event_id
else → skip

value == 1 → user.u = 0; user.d = 0
```

PHP calls `openEvent` after `buyBy*` and before final user save — mirror that order so period logic (e.g. reset_price already zeros traffic) can still be overridden by event=1, matching PHP.

## Compatibility

- Same keys / values as PHP admin save.
- Toggle binds boolean ↔ 0/1 on load/save if UI already coerces ints for other toggles; follow existing Toggle pattern in AdminSystemConfigView.

## Tradeoffs

- Keep `*_event_id` name (odd for a toggle) for PHP DB compatibility vs rename — choose keep keys.
