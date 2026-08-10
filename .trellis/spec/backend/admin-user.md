# Admin User & Ticket

> Executable contracts for `/api/v1/admin/user` and `/api/v1/admin/ticket`.

---

## Overview

Shares PHP `v2_user` / `v2_ticket` tables. JSON is SNAKE_CASE. User traffic on the **user row** is stored in **bytes**; plan `transfer_enable` remains **GB**.

---

## Units (user row)

| Field | Storage | Admin UI |
|-------|---------|----------|
| `balance`, `commission_balance` | cents | yuan `/100` |
| `transfer_enable`, `u`, `d` | **bytes** | GB `/1073741824` on display; `*1073741824` on save |
| `expired_at`, `created_at`, … | unix seconds | custom DateTimePicker or local string |
| `t` | unix seconds \| null/0 | 最近使用（节点流量上报写入）；列表「最近使用」列 |
| `remarks` | text nullable | list column + edit textarea |
| `is_admin`, `is_staff`, `banned` | 0/1 | toggles / selects |

---

## Scenario: User remarks + staff flags

### 1. Scope / Trigger

- Trigger: PHP `v2_user.remarks` / `is_staff` must appear in admin list and update payload (was missing from Java `User` model).

### 2. Signatures

```
GET  /api/v1/admin/user/fetch?current&pageSize&filter[i][key|condition|value]
GET  /api/v1/admin/user/getUserInfoById?id=
POST /api/v1/admin/user/update   JSON map
POST /api/v1/admin/user/generate JSON map
POST /api/v1/admin/user/delUser  form id
POST /api/v1/admin/user/resetSecret form id
GET  /api/v1/admin/user/getSubscribeUrl?id=   → data: string subscribe URL (ConfigService.buildSubscribeUrl)
```

DB: `v2_user.remarks` TEXT NULL; `v2_user.is_staff` TINYINT.

### 3. Contracts

| Field | Request / Response |
|-------|--------------------|
| `remarks` | string \| null — always include in `userToMap`; update accepts key even if `""` |
| `is_staff` | 0 \| 1 |
| filter key `remarks` | condition `模糊` → SQL LIKE |
| filter key `expired` | virtual; see scenario below |
| filter keys | `id`, `email`, `plan_id`, `transfer_enable`, `d`, `invite_user_id`, `invite_by_email`, `banned`, `uuid`, `token`, `remarks`, `expired` |
| `sort` / `sort_type` | see scenario below |

List row also exposes `plan_name`, `total_used` (= u+d bytes).

`fetch` also returns `stats` (same filter scope as `total`, not limited to the current page):

| Field | Meaning |
|-------|---------|
| `stats.banned` | `banned=1` count |
| `stats.with_plan` | `plan_id IS NOT NULL` count |
| `stats.expired` | `expired_at IS NOT NULL AND expired_at < now` count |

### 4. Validation & Error Matrix

| Condition | Error |
|-----------|-------|
| update missing id | 参数错误 |
| email taken | 邮箱已被使用 |
| plan_id invalid | 订阅计划不存在 |
| generate empty prefix | 邮箱前缀不能为空 |

### 5. Good / Base / Bad Cases

- **Good**: List shows `remarks` e.g. `接艳`; edit saves textarea.
- **Base**: `remarks=""` clears note; `is_staff=1` persists.
- **Bad**: Entity without `remarks` field → column silent-null in JSON, UI always `—`.

### 6. Tests Required

- fetch includes `remarks` / `is_staff` keys.
- update with `remarks` changes DB; filter `remarks`+`模糊` returns row.
- User traffic: save `transfer_gb=100` → DB ≈ `100 * 1073741824`.

### 7. Wrong vs Correct

#### Wrong

```java
// User.java missing remarks → never returned
m.put("email", u.getEmail());
```

#### Correct

```java
m.put("remarks", u.getRemarks());
m.put("is_staff", u.getIsStaff());
if (params.containsKey("remarks")) {
    Object r = params.get("remarks");
    user.setRemarks(r == null ? null : String.valueOf(r));
}
```

---

## Scenario: List expired filter + total_used sort

### 1. Scope / Trigger

- Trigger: Admin UI needs 已过期/未过期筛选 and 已用流量排序; `total_used` is not a DB column.

### 2. Signatures

```
GET /api/v1/admin/user/fetch?current&pageSize&sort&sort_type&filter[i][key|condition|value]
```

### 3. Contracts

| Item | Contract |
|------|----------|
| `sort=total_used` | `ORDER BY (IFNULL(u,0)+IFNULL(d,0)) ASC\|DESC` — fixed expression only |
| `sort=expired_at` | column `orderBy` on `expired_at` (null = 长期；MySQL NULL 排序规则) |
| other `sort` | must match `^[a-zA-Z_][a-zA-Z0-9_]*$` or fall back to `created_at` |
| `sort_type` | `ASC` \| `DESC`; invalid → `DESC` |
| default sort | `created_at` + `DESC` |
| filter `expired` + `=` + `1` | `expired_at IS NOT NULL AND expired_at < now` (unix sec) |
| filter `expired` + `=` + `0` | `expired_at IS NULL OR expired_at >= now` (长期 = 未过期) |
| other `expired` condition/value | ignore filter |
| `selectCount` | before attaching `ORDER BY` / `LIMIT` via `wrapper.last` |

UI (`AdminUsersView`): expired quick tabs; traffic header toggles `total_used` DESC↔ASC; clear resets to `created_at DESC` + expired all.

### 4. Validation & Error Matrix

| Condition | Behavior |
|-----------|----------|
| illegal sort identifier | coerce to `created_at` |
| `expired` value not `0`/`1` | skip that filter |
| `pageSize < 10` | coerce to 10 |

### 5. Good / Base / Bad Cases

- **Good**: `expired=0` includes `expired_at=null`; `sort=total_used&sort_type=DESC` orders by u+d.
- **Base**: No expired filter / default sort → same as pre-feature.
- **Bad**: `sort=u);DROP TABLE` → rejected by identifier regex, not interpolated into SQL.

### 6. Tests Required

- fetch with `expired=1` excludes null `expired_at` and future expiry.
- fetch with `expired=0` includes null and `expired_at >= now`.
- `sort=total_used` order matches `(u+d)`; `total` unchanged by sort.
- remarks `模糊` still LIKE-matches.

### 7. Wrong vs Correct

#### Wrong

```java
wrapper.orderBy(true, asc, sort); // sort=total_used → unknown column / injection risk
```

#### Correct

```java
if ("total_used".equals(sort)) {
    wrapper.last("ORDER BY (IFNULL(u,0)+IFNULL(d,0)) " + dir + " LIMIT ...");
} else if (sort.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
    wrapper.orderBy(true, asc, sort);
}
```

---

## Scenario: Admin user login log

### 1. Scope / Trigger

- Trigger: 「TA 的登录」需要可分页历史；PHP 核心无此表，Java 新增 `v2_user_login_log`.

### 2. Signatures

```
GET /api/v1/admin/user/getLoginLog?user_id&current&pageSize
DDL: src/main/resources/db/v2_user_login_log.sql
```

Write path: `AuthService.generateAuthData` → insert log + update `v2_user.last_login_at`.

### 3. Contracts

| Field | Type | Notes |
|-------|------|-------|
| `user_id` | long | required |
| `ip` | string ≤64 | `X-Forwarded-For` first / `X-Real-IP` / remote |
| `user_agent` | string ≤512 | truncated |
| `created_at` | unix sec | |

Response: `{ data: LoginLog[], total, email, last_login_at }`.

### 4. Validation & Error Matrix

| Condition | Behavior |
|-----------|----------|
| missing user | 用户不存在 |
| insert log fails | login still succeeds (warn log) |
| `pageSize < 10` | coerced to 10; max 100 |

### 5. Good / Base / Bad Cases

- **Good**: After passport/admin login, new row appears for that user.
- **Base**: Empty history → `data=[]`, UI empty state.
- **Bad**: Relying only on Redis `USER_SESSIONS_*` (lost on flush, not full history).

### 6. Tests Required

- Login → row count +1; `last_login_at` updated.
- `getLoginLog` pages by `created_at` DESC.
- `delUser` removes login logs for that user.

### 7. Wrong vs Correct

#### Wrong

```java
// only Redis session meta — admin history disappears after cache TTL
```

#### Correct

```java
userLoginLogMapper.insert(log); // durable
user.setLastLoginAt(now);
```

---

## Scenario: Ticket reply content-type + email filter

### 1. Scope / Trigger

- Trigger: Admin UI must match Spring `@RequestParam` reply; email filter is fuzzy.

### 2. Signatures

```
GET  /api/v1/admin/ticket/fetch?id=           → detail + messages
GET  /api/v1/admin/ticket/fetch?current&pageSize&status&reply_status&email
POST /api/v1/admin/ticket/reply   form: id, message   (NOT JSON body)
POST /api/v1/admin/ticket/close   form: id
```

### 3. Contracts

| Item | Contract |
|------|----------|
| `status` | 0 open, 1 closed |
| `reply_status` | 0 awaiting admin, 1 admin replied |
| `email` query | LIKE match on `v2_user.email`, then `user_id IN (...)` |
| message `is_me` | admin view: true when message.userId ≠ ticket.userId |

### 4. Validation & Error Matrix

| Condition | Error |
|-----------|-------|
| reply empty message | 消息不能为空 |
| ticket missing | 工单不存在 |

### 5–7. Summary

- **Wrong**: `Content-Type: application/json` + `JSON.stringify({id,message})` → param bind fails.
- **Correct**: `application/x-www-form-urlencoded` with `id` + `message`.
- Tests: reply with form succeeds; email substring filters tickets.

---

## Scenario: User ticket open gate from DB `ticket.ticket_status`

### 1. Scope / Trigger

- Trigger: Admin「系统配置 → 工单状态」保存后，用户端开单 / 提现开单立即按 DB 生效（非仅 yml）。

### 2. Signatures

```
ConfigService.getTicketStatus() → intFromGroup("ticket","ticket_status"), fallback yml v2board.ticket-status / 0
POST /api/v1/user/ticket/save
POST /api/v1/user/ticket/withdraw
GET  /api/v1/user/getSubscribe → includes ticket_status
```

### 3. Contracts

| `ticket_status` | Meaning |
|-----------------|---------|
| 0 | 任意登录用户可开单 |
| 1 | 仅有订单 status ∈ {3,4}（已完成/折抵）的用户可开单 |
| 2 | 禁止开单；`save` / `withdraw` 均拒绝「工单系统已关闭」 |

- 已有工单的 `reply` / `close` 不受此开关影响。
- 用户端 TicketView：`ticket_status === 2` 时隐藏「发起新工单」。

### 4. Wrong vs Correct

#### Wrong

```java
@Value("${v2board.ticket-status:0}")
private Integer ticketStatus; // gate only sees bootstrap yml
```

#### Correct

```java
switch (configService.getTicketStatus()) { ... }
```

### 5. Tests

- `ConfigServiceTicketStatusTest`: DB override vs yml fallback.
