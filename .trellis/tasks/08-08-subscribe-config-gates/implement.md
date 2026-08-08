# Implement: subscribe gates + order pricing

## Checklist

1. [x] ConfigService getters: plan_change / surplus / reset_traffic (+ allow_new_period)
2. [x] CouponService (check + use) + unit tests
3. [x] OrderService: setVipDiscount, setOrderType + surplus, applyBalance
4. [x] OrderController#save: full pipeline + coupon_code + allow_new_period renew check
5. [x] ResetSchedule: null plan method → ConfigService
6. [x] UI: PlanView period lock + coupon field; createOrder param
7. [x] Spec update (admin-commerce)
8. [ ] Manual AC1–AC10

## Validation

```bash
mvn -Dtest=CouponServiceTest,OrderServiceSetOrderTypeTest test
```
