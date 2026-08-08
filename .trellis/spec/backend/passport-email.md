# Passport Email Verify & Forget

> Unauthenticated send-code / register-with-code / forget-password contracts.

---

## Scenario: Email verification code + password reset

### 1. Scope / Trigger

- Trigger: Admin `safe.email_verify` and forget-password UX need working SMTP + code APIs.
- Symptom if broken: UI shows success but no mail (nested SMTP unread); register fails when verify on without `email_code`.

### 2. Signatures

```
POST /api/v1/passport/comm/sendEmailVerify
POST /api/v1/passport/auth/register
POST /api/v1/passport/auth/forget
```

- `PassportService.sendEmailVerify` / `register` / `forget`
- `MailService.sendEmail` (async jobs) / `sendEmailSync` (admin test only)
- SMTP settings: `ConfigService.getStringFromGroup("email", key)` — **not** top-level `getFullConfig().get(key)`

### 3. Contracts

**sendEmailVerify** JSON body:

| Field | Type | Notes |
|-------|------|-------|
| `email` | string | required |
| `isforget` | 0/1 | `0` = register (email must not exist); `1` = forget (email must exist) |

Side effects: 6-digit code in Redis `EMAIL_VERIFY_CODE` (300s); resend lock `LAST_SEND_EMAIL_VERIFY_TIMESTAMP` (60s); IP rate on send.

**register** when `safe.email_verify=1`: body must include `email_code` matching cache.

**forget** always requires `email` + 6-digit `email_code` + `password` (independent of `email_verify` flag).

**SMTP** (admin system config `email.*` only; no `MAIL_*` env / `spring.mail` yml):

| Key | Notes |
|-----|-------|
| `email_host` / `email_port` / `email_username` / `email_password` | Applied in `MailService.applyDynamicMailConfig` |
| `email_encryption` | `ssl` / `tls` / empty (clear ssl+starttls) |
| `email_from_address` | MimeMessage From |
| `email_template` | `default` → `mail/{name}`; else `mail/{folder}/{name}` |

`JavaMailSender` bean from `MailConfig` (empty impl; runtime override).

### 4. Validation & Error Matrix

| Condition | Result |
|-----------|--------|
| Missing email on send | 422 / business error |
| `isforget=0` + email exists | 「This email is registered」 |
| `isforget=1` + email missing | 「This email is not registered…」 |
| Resend < 60s | 「…please request again later」 |
| IP send rate ≥ 3 / 60s | 429 |
| Register + verify on + bad code | 「Incorrect email verification code」 |
| Forget wrong code | increments `FORGET_REQUEST_LIMIT`; fail |
| Nested SMTP unread (top-level get) | Falls back to empty host → send fails |

### 5. Good/Base/Bad Cases

- Good: Admin SMTP + `email_verify=1` → send `isforget=0` → register with code → session.
- Base: `email_verify=0` → register without `email_code`; forget still needs code.
- Bad: `config.get("email_host")` on nested full config; admin test using async `sendEmail` only (false success).

### 6. Tests Required

- Unit: `ConfigServiceEmailNestedTest` — nested email keys via `getStringFromGroup`
- Unit: `MailServiceConfigTest` — applyDynamicMailConfig / encryption clear / template path
- Unit: `CommControllerConfigTest` — public flags only (no SMTP secrets)
- Manual: admin「发送测试邮件」sync path; user send + register/forget with real SMTP

### 7. Wrong vs Correct

#### Wrong

```java
Object val = configService.getFullConfig().get("email_host"); // always null
```

#### Correct

```java
String host = configService.getStringFromGroup("email", "email_host");
```

---

## Design Decision: Forget always requires email code

**Context**: PHP forget always validates a mailed code; `email_verify` only gates register.

**Decision**: UI always offers `/forget`; send uses `isforget=1`. Do not hide forget behind `email_verify`.

**Related**: Frontend `ForgetView.vue`, `auth.ts` `sendEmailVerify` / `forgetPassword`; public config does not need a forget flag.

---

## Scenario: reCAPTCHA on user login/register

### 1. Scope / Trigger

- Admin `safe.recaptcha_enable` + keys; user login and register (not admin login / forget / sendEmailVerify).

### 2. Signatures

- `RecaptchaService.verifyIfEnabled(token, remoteIp)`
- Login: optional form field `recaptcha_data`
- Register JSON: `recaptcha_data`
- Public: `recaptcha_enable` + `recaptcha_site_key` only

### 3. Contracts

| Condition | Behavior |
|-----------|----------|
| enable=0 | No-op |
| enable=1, empty secret | 500 `"reCAPTCHA 未正确配置"` |
| enable=1, missing/invalid token | 500 `"Invalid code is incorrect"` |
| Google siteverify success | Continue auth |

Secret key is `safe.recaptcha_key` (server-only).
