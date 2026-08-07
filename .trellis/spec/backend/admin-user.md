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
```

DB: `v2_user.remarks` TEXT NULL; `v2_user.is_staff` TINYINT.

### 3. Contracts

| Field | Request / Response |
|-------|--------------------|
| `remarks` | string \| null — always include in `userToMap`; update accepts key even if `""` |
| `is_staff` | 0 \| 1 |
| filter key `remarks` | condition `模糊` → SQL LIKE |
| filter keys | `id`, `email`, `plan_id`, `transfer_enable`, `d`, `invite_user_id`, `invite_by_email`, `banned`, `uuid`, `token`, `remarks` |

List row also exposes `plan_name`, `total_used` (= u+d bytes).

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
