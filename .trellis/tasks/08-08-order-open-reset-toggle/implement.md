# Implement: 订单开通清零流量开关

## Checklist

1. `ConfigService`: getters `getNewOrderEventId` / renew / change (int, default 0) if missing.
2. `OrderService.open`: after buyBy* / before `userMapper.updateById`, apply openEvent by type.
3. Unit test: type=2 + renew_event_id=1 → u/d cleared; event_id=0 → unchanged (mock config).
4. UI `AdminSystemConfigView`: Toggle + Chinese labels/desc for three fields.
5. Spec note in `admin-commerce.md` or subscribe config guide if present.

## Validation

```bash
mvn -Dtest=OrderServiceOpenEventTest test
# or method under existing OrderService*Test
```

## Rollback

Revert openEvent call + UI Toggle; config keys unchanged.
