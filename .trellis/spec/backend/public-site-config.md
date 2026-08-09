# Public Site Config

> Unauthenticated public config for brand + register/safe-mode gates + client API prefixes.
> Whole HTTP JSON body is one SM4-CBC envelope (no double encryption).

---

## Scenario: Public config bootstrap (SM4 outer envelope)

### 1. Scope / Trigger

- Trigger: Login/register need `app_name`, register gates, and **passport/user/admin API prefixes** without JWT.
- Do **not** expose `/api/v1/admin/config/fetch` publicly — it returns full nested config including secrets.
- Classic `GET /api/v1/passport/comm/config` is **removed** (hard cutover with other passport routes).
- Bootstrap path: `site.public_config_path` (auto `/c/`+8); frontend `VITE_PUBLIC_CONFIG_PATH` must match.

### 2. Signatures

- `GET {public_config_path}` — `CommController#config` (registered by `PublicConfigRouteRegistrar`)
- `ConfigService.getAppName()` / `getStopRegister()` / `getInviteForce()` / `getEmailVerify()` / `getSafeModeEnable()` / `getSecurePath()` / `getRecaptchaEnable()` / `getRecaptchaSiteKey()`
- `ConfigService.getFrontendThemeSidebar()` / `getFrontendThemeHeader()` / `getFrontendThemeColor()` / `getFrontendBackgroundUrl()`
- `ConfigService.getTelegramDiscussLink()`
- `ConfigService.ensureClientApiPaths()` → `passport_api_prefix` / `user_api_prefix` / `admin_api_prefix` / `public_config_path`
- Outer encrypt: `PanelSm4Filter` + `Sm4Util.encryptToEnvelope` / `parseKey(SM4_KEY)`
- Env: `SM4_KEY` → `v2board.sm4-key` (required)

### 3. Contracts

**Request**: none (no auth). Marked panel-SM4 zone (response encrypted); no classic auth.

**Controller `data` (plaintext fields inside ApiResponse, before outer encrypt)**:

| Field | Type | Notes |
|-------|------|-------|
| `app_name` | string | Non-empty |
| `stop_register` | int 0/1 | |
| `invite_force` | int 0/1 | |
| `email_verify` | int 0/1 | |
| `safe_mode_enable` | int 0/1 | |
| `secure_path` | string | empty/invalid → `"admin"` |
| `recaptcha_enable` | int 0/1 | |
| `recaptcha_site_key` | string | never secret `recaptcha_key` |
| `frontend_theme_*` / `frontend_background_url` | string | |
| `telegram_discuss_link` | string | empty allowed |
| `passport_api_prefix` | string | replaces `/api/v1/passport` |
| `user_api_prefix` | string | replaces `/api/v1/user` |
| `admin_api_prefix` | string | replaces `/api/v1/admin` |
| `public_config_path` | string | this bootstrap path |

Never expose `telegram_bot_token` / `frontend_theme` package name here.

**Wire response**: entire body is `{iv,payload}` whose plaintext is `ApiResponse` JSON (`code`/`message`/`data`). No inner envelope in `data`.

Frontend: `VITE_SM4_KEY` + `VITE_PUBLIC_CONFIG_PATH`; decrypt once → parse ApiResponse → use `data` (`site.ts`).

### 4. Validation & Error Matrix

| Condition | Behavior |
|-----------|----------|
| `SM4_KEY` empty | Filter 500 `"SM4 key not configured"` — **no plaintext fallback** |
| Invalid key length | 500 `"SM4 key invalid: …"` |
| Encrypt failure | 500 `"响应加密失败"` |
| Auth missing | Still allowed (public) |

### 5. Good/Base/Bad Cases

- Good: GET public path → outer envelope; decrypt → ApiResponse with prefixes in `data`.
- Base: Dev key `0123456789abcdef`; align `VITE_PUBLIC_CONFIG_PATH` with admin site field.
- Bad: Double SM4 (controller encrypts `data` again); classic passport config path still live.

### 6. Tests Required

- Unit: `Sm4UtilTest` / `PanelSm4SupportTest` — envelope + compact round-trip
- Unit: `CommControllerConfigTest` — plaintext `data` includes prefixes; no `iv`/`payload` in `data`
- Unit: `ConfigServiceClientApiPrefixTest` — auto-gen / validation

### 7. Wrong vs Correct

#### Wrong

```java
return ApiResponse.success(Sm4Util.encryptToEnvelope(json, key)); // double SM4 with PanelSm4Filter
```

#### Correct

```java
return ApiResponse.success(plainMap); // PanelSm4Filter wraps whole ApiResponse
```

---

## Design Decision: Neutral public_config_path bootstrap

**Context**: Public config must reveal passport/user/admin prefixes, but those prefixes cannot be known before config load.

**Decision**: Dedicated `site.public_config_path` + matching `VITE_PUBLIC_CONFIG_PATH`; not under passport prefix.

---

## Design Decision: Fail closed when SM4_KEY missing

**Decision**: Missing/invalid key → 500; never plaintext fallback.

---

## Common Mistake: Treating SM4 as confidentiality

**Fix**: Transport obfuscation only; never put secrets in this payload.
