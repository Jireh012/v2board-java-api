# Design: 充值奖励与用户充值

## API

- `OrderService.getBonus(totalAmountCents)`:
  - Read `getFullConfig().get("deposit")` → map → `deposit_bounus`
  - Accept `List` / array of `"yuan:yuan"` strings
  - Convert tiers with `* 100`; pick max bonus where `totalAmount >= threshold`
- Prefer `ConfigService` helper `getDepositBonusTiers()` if cleaner.
- Unit tests with mocked nested config.

## UI

- `createOrder` support optional `depositAmount` cents when `planId === 0`.
- `ProfileView`: modal — amount in yuan → `Math.round(yuan * 100)` → create deposit order → `router.push(/order/{tradeNo})`.
- Order detail already supports checkout for status=0.

## Compatibility

Keep key typo `deposit_bounus` (PHP).
