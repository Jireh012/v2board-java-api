# Public Site Config

> Unauthenticated public config for brand + register/safe-mode gates. Response `data` is SM4-CBC encrypted.

---

## Scenario: Public passport comm config (SM4 envelope)

### 1. Scope / Trigger

- Trigger: Login/register/chrome need `app_name` and register gates without JWT.
- Do **not** expose `/api/v1/admin/config/fetch` publicly — it returns full nested config including secrets.
- Wire format: plaintext JSON fields encrypted with SM4-CBC before leaving the API.

### 2. Signatures

- `GET /api/v1/passport/comm/config` — `CommController#config`
- `ConfigService.getAppName()` / `getStopRegister()` / `getInviteForce()` / `getEmailVerify()` / `getSafeModeEnable()` / `getSecurePath()` / `getRecaptchaEnable()` / `getRecaptchaSiteKey()`
- `ConfigService.getFrontendThemeSidebar()` / `getFrontendThemeHeader()` / `getFrontendThemeColor()` / `getFrontendBackgroundUrl()` — nested `frontend.*` via `getStringFromGroup`
- `ConfigService.getTelegramDiscussLink()` — nested `telegram.telegram_discuss_link` (public; may be empty)
- `Sm4Util.encryptToEnvelope(plaintext, key)` / `parseKey(SM4_KEY)`
- Env: `SM4_KEY` → `v2board.sm4-key` (required for this endpoint)

### 3. Contracts

**Request**: none (no auth). Path outside `ClientAuthInterceptor`.

**Plaintext JSON** (before encrypt, snake_case):

| Field | Type | Notes |
|-------|------|-------|
| `app_name` | string | Non-empty |
| `stop_register` | int 0/1 | `1` = registration closed |
| `invite_force` | int 0/1 | `1` = invite code required |
| `email_verify` | int 0/1 | `1` = register requires email code |
| `safe_mode_enable` | int 0/1 | `1` = user UI requires login except auth pages |
| `secure_path` | string | Admin UI path segment; empty/invalid stored → expose `"admin"`; custom must be ≥8 alphanumeric and not reserved |
| `recaptcha_enable` | int 0/1 | `1` = user login/register require reCAPTCHA v2 |
| `recaptcha_site_key` | string | Public site key only — **never** `recaptcha_key` (secret) |
| `frontend_theme_sidebar` | string | `light` / `dark`; default `light` |
| `frontend_theme_header` | string | `light` / `dark`; default `dark` |
| `frontend_theme_color` | string | `default` / `darkblue` / `black` / `green`; default `default` |
| `frontend_background_url` | string | Optional background image URL; empty allowed |
| `telegram_discuss_link` | string | Telegram group/discuss invite URL; empty allowed |

Note: `frontend_theme` (legacy PHP theme package name) is **not** exposed on this public endpoint; admin save still persists it for PHP compat. Never expose `telegram_bot_token` here.

**Response** `data` (encrypted envelope only):

| Field | Type | Notes |
|-------|------|-------|
| `iv` | string | base64, 16-byte IV |
| `payload` | string | base64, SM4-CBC/PKCS7 ciphertext of UTF-8 JSON |

Algorithm: `SM4/CBC/PKCS7Padding` (BouncyCastle). Key: 16 UTF-8 bytes **or** 32 hex chars. No plaintext business fields alongside envelope.

Frontend: `VITE_SM4_KEY` must match; decrypt then parse JSON (`site.ts`).

### 4. Validation & Error Matrix

| Condition | Behavior |
|-----------|----------|
| `SM4_KEY` empty | `BusinessException` 500 `"SM4 key not configured"` — **no plaintext fallback** |
| Invalid key length | 500 `"SM4 key invalid: …"` |
| Encrypt failure | 500 `"加密公开配置失败"` |
| Auth missing | Still allowed (public) |

### 5. Good/Base/Bad Cases

- Good: Valid key → `data` has only `iv`/`payload`; decrypt yields public fields including `secure_path`.
- Base: Dev key in `application-dev.yml` / `.env.example` sample.
- Bad: Returning plaintext map; dual plaintext+ciphertext; reusing `APP_KEY` as SM4 key without documenting.

### 6. Tests Required

- Unit: `Sm4UtilTest` — round-trip UTF-8/hex keys; random IV per encrypt
- Unit: `CommControllerConfigTest` — envelope size 2; decrypt asserts public fields (incl. frontend theme keys); no `app_name` at envelope top level
- Unit: `ConfigServiceFrontendThemeTest` — defaults light/dark/default/"" when unset

### 7. Wrong vs Correct

#### Wrong

```java
return ApiResponse.success(plainMap); // plaintext leak
```

#### Correct

```java
String json = objectMapper.writeValueAsString(plainMap);
return ApiResponse.success(Sm4Util.encryptToEnvelope(json, Sm4Util.parseKey(sm4Key)));
```

---

## Design Decision: Mount on passport CommController

**Context**: Login pages cannot call admin config APIs.

**Decision**: `GET /api/v1/passport/comm/config` on existing `CommController` — already unauthenticated.

**Related**: Frontend `v2board-ui` decrypt in `src/api/site.ts`; brand flags in `siteBrand.ts`.

---

## Design Decision: Fail closed when SM4_KEY missing

**Context**: Tempting to return plaintext if key unset for “easier local dev”.

**Decision**: Missing/invalid key → HTTP business 500; never plaintext fallback. Dev samples live in `application-dev.yml` / `.env.example` (`0123456789abcdef`).

---

## Common Mistake: Treating SM4 as confidentiality

**Symptom**: Expecting network observers without the frontend bundle to be unable to read config.

**Cause**: `VITE_SM4_KEY` is shipped in the UI build; anyone with the JS can decrypt.

**Fix / Prevention**: Document as **transport obfuscation** only. Do not put secrets in this public config. Keep SMTP tokens etc. off this endpoint.
