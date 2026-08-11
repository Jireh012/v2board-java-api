# Admin Commerce (Orders / Coupons / Giftcards / Plans)

> Executable contracts for admin money-related APIs that share PHP V2Board tables.

---

## Overview

Admin controllers under `/api/v1/admin/{order,coupon,giftcard,plan}` return `ApiResponse` with Jackson **SNAKE_CASE**. Amounts and traffic units below are PHP-compatible and must stay consistent with the Vue admin UI.

---

## Scenario: User order list fetch (lazy page)

### 1. Scope / Trigger

- `GET /api/v1/user/order/fetch` powers「我的订单」infinite scroll and PlanView period lock.

### 2. Signatures

```
GET /api/v1/user/order/fetch?status&current&pageSize
```

### 3. Contracts

| Mode | Request | Response `data` |
|------|---------|-----------------|
| Legacy (PHP) | no `pageSize` | `OrderRow[]` |
| Paginated | `pageSize` set (1–50; default coerce ≥1) | `{ data: OrderRow[], total }` |

- `current` defaults to 1 when paginating. Manual `LIMIT` (no MP pagination plugin).
- `status` optional filter (0 pending / 3 finished, etc.).

### 4. Wrong vs Correct

#### Wrong
Rely on `selectPage` without LIMIT plugin for user order pages.

#### Correct
`selectCount` + `last("LIMIT offset,size")` when `pageSize` present; omit `pageSize` for full list callers (`PlanView`).

---

## Scenario: User order save pricing pipeline

### 1. Scope / Trigger

- `POST /api/v1/user/order/save` must mirror PHP: coupon → VIP discount → order type/surplus → invite → balance.

### 2. Contracts

| Step | Behavior |
|------|----------|
| `coupon_code` | Optional; `CouponService.use` sets `discount_amount` + `coupon_id` (does not reduce total yet) |
| VIP `user.discount` | Adds % of current total into `discount_amount`, then `total -= discount_amount` |
| type=3 change | Requires `subscribe.plan_change_enable`; optional `surplus_enable` surplus math |
| renew period | If `allow_new_period=0`, period must match last completed same-plan order |
| balance | Auto-deduct; `balance_amount`; cancel refunds |

### 3. Wrong vs Correct

#### Wrong

```java
order.setType(3); // without plan_change_enable / surplus
```

#### Correct

```java
couponService.use(code, order);
orderService.setVipDiscount(order, user);
orderService.setOrderType(order, user);
orderService.applyBalance(order, user);
```

---

## Units (cross-layer)

| Field / concept | Storage | UI display / input |
|-----------------|---------|-------------------|
| Order / coupon / giftcard **money** | Integer **cents** (`分`) | Yuan with `/100` or `*100` |
| Invite `commission_withdraw_limit` / `commission_balance` | Integer **cents** (`分`) | Admin label: 分; compare balance vs limit in cents (see [invite-commission.md](./invite-commission.md)) |
| Coupon `type=1` value | Cents | Yuan |
| Coupon `type=2` value | Percent integer `1–100` | `%` (no `/100`) |
| Plan `transfer_enable` | **GB** (not bytes) | GB; on user assign multiply by `1073741824` |
| Giftcard type 3 value | GB | GB |
| Giftcard type 2 / 5 value | Days | Days (`0` on type 5 = no expiry) |
| Timestamps | Unix **seconds** | `datetime-local` ↔ `Math.floor(ms/1000)` |

> **Warning**: Treating plan `transfer_enable` as bytes makes admin forms show absurd numbers and corrupts user quotas on save.

---

## Scenario: Coupon generate / update

### 1. Scope / Trigger

- Trigger: New/changed `/api/v1/admin/coupon/*` payload and null-clearing on update.

### 2. Signatures

```
GET  /api/v1/admin/coupon/fetch?current&pageSize&sort_type
POST /api/v1/admin/coupon/generate   JSON body Coupon
POST /api/v1/admin/coupon/show      form: id
POST /api/v1/admin/coupon/drop      form: id
```

Entity `v2_coupon` / `Coupon.java`.

### 3. Contracts

| Field | Type | Notes |
|-------|------|-------|
| `name` | string | required |
| `code` | string | optional on create → server random |
| `type` | 1 \| 2 | **1 = amount (cents), 2 = percent** (Java model; not inverted) |
| `value` | int | >0; type 2 ≤ 100 |
| `show` | 0 \| 1 | |
| `limit_use` / `limit_use_with_user` | int \| null | null = unlimited |
| `limit_plan_ids` / `limit_period` | JSON string \| null | e.g. `"[1,2]"`, `"[\"month_price\"]"` |
| `started_at` / `ended_at` | unix sec | required; `ended_at > started_at` |

Response list: `{ data: Coupon[], total: number, stats }` — `stats` is **full-table** (not current page):

| Field | Meaning |
|-------|---------|
| `stats.showing` | `show=1` |
| `stats.active` | enabled and within `[started_at, ended_at]` at request time |
| `stats.expired` | `ended_at < now` |

### 4. Validation & Error Matrix

| Condition | Error message (code 500) |
|-----------|--------------------------|
| Empty name | 优惠券名称不能为空 |
| type not 1/2 | 优惠券类型有误 |
| value ≤ 0 | 优惠值有误 |
| type 2 and value > 100 | 比例优惠不能超过 100% |
| missing period | 请设置有效期 |
| ended ≤ started | 结束时间必须晚于开始时间 |
| update missing id | 优惠券不存在 |

### 5. Good / Base / Bad Cases

- **Good**: Create type 1 value `990` (= ¥9.90), `limit_plan_ids=null`.
- **Base**: Edit clears plan limits by sending `null` / `[]` → stored NULL via `LambdaUpdateWrapper`.
- **Bad**: `updateById` with omitted nulls — MyBatis-Plus **ignores null**, old limits stick.

### 6. Tests Required

- Create coupon without `code` → non-empty generated code.
- Update with `limit_plan_ids=null` → DB column NULL.
- Reject type 2 value 101.

### 7. Wrong vs Correct

#### Wrong

```java
couponMapper.updateById(body); // null fields not cleared
```

#### Correct

```java
LambdaUpdateWrapper<Coupon> uw = new LambdaUpdateWrapper<>();
uw.eq(Coupon::getId, id)
  .set(Coupon::getLimitPlanIds, emptyToNull(body.getLimitPlanIds()))
  .set(Coupon::getLimitPeriod, emptyToNull(body.getLimitPeriod()));
couponMapper.update(null, uw);
```

---

## Scenario: Giftcard generate / update

### 1. Scope / Trigger

- Trigger: `/api/v1/admin/giftcard/*` types and redeem semantics alignment.

### 2. Signatures

```
GET  /api/v1/admin/giftcard/fetch?current&pageSize&sort_type
POST /api/v1/admin/giftcard/generate   JSON Giftcard
POST /api/v1/admin/giftcard/drop       form: id
```

Redeem (user): `POST /api/v1/user/redeemGiftCard` form `giftcard=<code>`.

### 3. Contracts

| `type` | Meaning | `value` | `plan_id` |
|--------|---------|---------|-----------|
| 1 | Balance | cents | null |
| 2 | Extend expiry | days | null |
| 3 | Add traffic | GB | null |
| 4 | Clear used u/d | 0 | null |
| 5 | Assign plan | days (0 = unlimited) | **required** |

`used_user_ids`: JSON array or Java `Set.toString()`-like `[1, 2]`; admin UI parses both.

`GET …/giftcard/fetch` also returns `stats` over the **full table** (not current page):

| Field | Meaning |
|-------|---------|
| `stats.available` | not expired, started, and `limit_use` null or `> 0` |
| `stats.used_up` | `limit_use` not null and `<= 0` |
| `stats.expired` | `ended_at < now` |

### 4. Validation & Error Matrix

| Condition | Error |
|-----------|-------|
| Empty name | 礼品卡名称不能为空 |
| type ∉ 1..5 | 礼品卡类型有误 |
| type 5 without plan_id | 请选择指定套餐 |
| type ≠ 4 and value invalid | 面值有误 / 必须大于 0 |
| bad period | 请设置有效期 / 结束时间必须晚于开始时间 |

### 5. Good / Base / Bad Cases

- **Good**: type 1 value `1000` (= ¥10).
- **Base**: type 5 plan_id set, value `0` → user `expired_at=null`.
- **Bad**: Leaving `plan_id` set when switching type away from 5 (update must force `plan_id=null`).

### 6. Tests Required

- Generate without code → 16-char code.
- Update type 1→5 requires plan_id.
- Update type 5→1 clears plan_id in DB.

### 7. Wrong vs Correct

#### Wrong

```java
// type flipped to 1 but plan_id left from previous edit
giftcardMapper.updateById(body);
```

#### Correct

```java
Long planId = body.getType() == 5 ? body.getPlanId() : null;
uw.set(Giftcard::getPlanId, planId);
```

---

## Scenario: Admin order detail — user email / remarks

### 1. Scope / Trigger

- Trigger: Cross-layer detail payload must expose buyer identity (`email`, `remarks`) and `plan_name`; order row alone only has `user_id` / `plan_id`.

### 2. Signatures

```
POST /api/v1/admin/order/detail  form: id=<order.id>
```

DB joins (read-only): `v2_user` by `order.user_id`; `v2_plan` by `order.plan_id`.

### 3. Contracts

| Field | Type | Source | Notes |
|-------|------|--------|-------|
| (all order columns) | — | `v2_order` | Same as list `orderToMap` |
| `email` | string \| null | `v2_user.email` | Joined; not on order table |
| `remarks` | string \| null | `v2_user.remarks` | Admin note; may be empty |
| `plan_name` | string \| null | `v2_plan.name` | Prefer over `套餐 #plan_id` in UI |
| `commission_log` | array | `v2_commission_log` | By `trade_no` |
| `surplus_orders` | array \| omit | `v2_order` | When `surplus_order_ids` parseable |

Money fields remain **cents**. List `fetch` does **not** require `email`/`remarks` on every row (detail-only enrichment).

### 4. Validation & Error Matrix

| Condition | Error / behavior |
|-----------|------------------|
| Unknown `id` | code 500, `订单不存在` |
| User missing for `user_id` | Still 200; omit/null `email`/`remarks` |
| Plan missing | Still 200; omit `plan_name` |
| Bad `surplus_order_ids` JSON | Ignore surplus list; rest of detail OK |

### 5. Good / Base / Bad Cases

- **Good**: Detail for user with remarks → JSON has `email`, `remarks`, `plan_name`.
- **Base**: `remarks=null` → UI shows `—` / omits remark segment; email still shown.
- **Bad**: Frontend invents email from list row (list has no email) without calling detail.

### 6. Tests Required

- `detail` with valid id → response contains `email` matching `v2_user` and `plan_name` when plan exists.
- Deleted/missing user → detail still succeeds; `email`/`remarks` absent or null.
- Assert money fields still integers (cents), not yuan strings.

### 7. Wrong vs Correct

#### Wrong

```java
return ApiResponse.success(orderToMap(order)); // only user_id — UI cannot show buyer email
```

#### Correct

```java
Map<String, Object> data = orderToMap(order);
User user = userMapper.selectById(order.getUserId());
if (user != null) {
    data.put("email", user.getEmail());
    data.put("remarks", user.getRemarks());
}
```

**UI**: Hero user chip is **one line** `email · #id · remarks` (ellipsis); 订单信息 also lists 用户邮箱 / 用户备注. Modal class `modal-detail` (~960px) in `admin.css`.

---

## Scenario: Admin order list filters

### 1. Scope / Trigger

- Trigger: `GET /api/v1/admin/order/fetch` filter query contract used by Vue admin search.

### 2. Signatures

```
GET /api/v1/admin/order/fetch?current&pageSize&is_commission
  &filter[i][key]&filter[i][condition]&filter[i][value]
```

### 3. Contracts

| Key | Allowed | Typical condition |
|-----|---------|-------------------|
| `trade_no`, `email`, `callback_no` | yes | **`模糊`** (LIKE) |
| `user_id`, `invite_user_id`, `status`, `commission_status` | yes | `=` |
| condition whitelist | `>`, `<`, `=`, `>=`, `<=`, `模糊`, `!=` | Admin UI no longer exposes `>`/`<` |

Response: `{ data: OrderRow[], total: number, stats }` (+ `plan_name` on rows).

`stats` uses the **same filter / commission scope as `total`**, not the current page:

| Field | Meaning |
|-------|---------|
| `stats.pending` | `status=0` count |
| `stats.completed` | `status=3` count |
| `stats.amount_cents` | `SUM(total_amount)` in cents |

Pagination: this project has **no** MyBatis-Plus `PaginationInnerInterceptor`. `fetch` must `selectCount` + `selectList` with `last("LIMIT offset,pageSize")` (same pattern as admin user/coupon). Do **not** rely on `selectPage` alone — it returns the full set and breaks admin UI paging. Do **not** compute admin stat cards from the current page rows only.

### 4. Validation & Error Matrix

| Condition | Behavior |
|-----------|----------|
| Unknown key/condition | Filter skipped (whitelist) |
| `email` + 模糊/= | Resolve user ids then filter `user_id` |
| `pageSize < 10` | Coerced to 10 |
| `current < 1` | Coerced to 1 |

### 5. Good / Base / Bad Cases

- **Good**: `filter[0][key]=trade_no&condition=模糊&value=202608` returns substring matches.
- **Base**: Status quick filter `status=` combined with trade_no fuzzy.
- **Bad**: Frontend sending `>` for trade_no (unsupported UX; prefer fuzzy).

### 6. Tests Required

- Fuzzy trade_no returns subset; exact status filter works.
- Response `total` matches count query (not 0 when data non-empty).

### 7. Wrong vs Correct

#### Wrong

```ts
filters.push({ key: 'trade_no', condition: '=', value }) // misses partial paste
```

#### Correct

```ts
const EXACT = new Set(['user_id', 'invite_user_id', 'status', 'commission_status'])
condition: EXACT.has(key) ? '=' : '模糊'
```

---

## Scenario: Plan save — traffic unit + null prices

### 1. Scope / Trigger

- Trigger: `/api/v1/admin/plan/save` clearing nullable prices and `transfer_enable` unit.

### 2. Signatures

```
POST /api/v1/admin/plan/save?force_update=false
JSON Plan (snake_case)
```

### 3. Contracts

| Field | Unit / notes |
|-------|----------------|
| `transfer_enable` | **GB** in DB/API |
| `*_price` | cents; null clears via `LambdaUpdateWrapper` |
| `force_update=true` | Sync users: `transfer_enable * 1073741824` bytes |

### 4–7. Summary

- Use `LambdaUpdateWrapper.set(...)` so null prices persist as SQL NULL.
- Never treat plan traffic as bytes in admin forms.
- Tests: save with `month_price=null` clears column; force_update multiplies GB→bytes on users.

---

## Scenario: Order open — clear used traffic toggles

### 1. Scope / Trigger

- Admin subscribe config keys `new_order_event_id` / `renew_order_event_id` / `change_order_event_id` (PHP-compatible 0/1).
- Applied in `OrderService.open` **after** `buyBy*` period logic and **before** final `userMapper.updateById`.

### 2. Contracts

| `order.type` | Config key | When value `== 1` |
|--------------|------------|-------------------|
| 1 新购 | `subscribe.new_order_event_id` | `user.u = 0`, `user.d = 0` |
| 2 续费 | `subscribe.renew_order_event_id` | same |
| 3 变更 | `subscribe.change_order_event_id` | same |
| other (reset/deposit/…) | — | skip these keys |

Only literal `1` enables clear (not other non-zero). Admin UI exposes Toggles that write 0/1; keys keep `*_event_id` names for PHP DB compatibility.

### 3. Wrong vs Correct

#### Wrong

```java
// after buyByPeriod but never read event ids — renew with event=1 keeps old u/d
userMapper.updateById(user);
```

#### Correct

```java
buyByPeriod(order, plan, user);
openEvent(order, user); // type→key; if eventId==1 then u=d=0
userMapper.updateById(user);
```

---

## Scenario: Deposit bonus on order open

### 1. Scope / Trigger

- Admin system config group `deposit.deposit_bounus` (PHP typo key, do **not** rename).
- Applied in `OrderService.open` when `order.type == 9` and `total_amount > 0`.
- Commission transfer orders (`type=9`, `total_amount=0`) must not grant bonus.

### 2. Contracts

| Item | Rule |
|------|------|
| Config path | `getFullConfig().deposit.deposit_bounus` (nested; not top-level) |
| Tier format | List / array of `"yuan:yuan"` strings, e.g. `"100:10"` |
| Units | Threshold & bonus stored as yuan in config → convert `* 100` to cents |
| Selection | Among tiers where `totalAmountCents >= thresholdCents`, take **max** bonus |
| Missing / empty / invalid lines | Bonus `0`; skip bad lines |

### 3. Wrong vs Correct

#### Wrong

```java
Object bonusObj = config.get("deposit_bounus"); // top-level — admin saves under deposit.*
```

#### Correct

```java
Map deposit = (Map) config.get("deposit");
Object bonusObj = deposit != null ? deposit.get("deposit_bounus") : null;
```

### 4. Tests Required

- Nested `100:10` + deposit 10000¢ → bonus 1000¢.
- Below threshold / missing section → 0.
- Multi-tier → max matching bonus.

---

## Design Decision: Update wrappers over `updateById`

**Context**: Clearing optional limits/prices must write NULL.

**Decision**: Admin save/generate for coupon, giftcard, and plan updates use `LambdaUpdateWrapper` explicit `.set` for nullable columns.

**Why**: MyBatis-Plus `updateById` skips nulls by default → stale JSON limits and prices.
