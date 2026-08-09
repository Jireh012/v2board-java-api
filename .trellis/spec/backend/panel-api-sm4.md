# Panel API Path Rewrite + SM4

> Passport / user / admin traffic uses configurable prefixes + panel `SM4_KEY`.
> Distinct from node SM4 (`server_token` derive) in [server-node.md](./server-node.md).
> Public bootstrap `GET /config` details: [public-site-config.md](./public-site-config.md).

---

## Scenario: Client API prefix hard-cutover

### 1. Scope / Trigger

- Trigger: Replace classic `/api/v1/{passport|user|admin}` with `site.*_api_prefix` to cut V2Board path fingerprints.
- Symptom if broken: classic paths still 200; rewritten paths 404; prefixes collide with `/config` or subscribe/node prefixes.

### 2. Signatures

```java
// ConfigService
Map<String,String> ensureClientApiPaths(); // auto-gen empty prefixes; persist
// keys: passport_api_prefix, user_api_prefix, admin_api_prefix, public_config_path(="/config")

static final String FIXED_PUBLIC_CONFIG_PATH = "/config";

// ClientApiPathRegistry — hot cache of prefixes; refresh on save / ensure
// ClientApiPathFilter — order HIGHEST_PRECEDENCE+20
```

Internal controllers keep `@RequestMapping("/api/v1/...")`; filter rewrites before dispatch.

### 3. Contracts

| External path | Internal mapping | Notes |
|---------------|------------------|-------|
| `{passport_api_prefix}/**` | `/api/v1/passport/**` | Marks panel SM4 + reject classic auth |
| `{user_api_prefix}/**` | `/api/v1/user/**` | same |
| `{admin_api_prefix}/**` | `/api/v1/admin/**` | same |
| `GET /config` | public config handler | Panel SM4 only (no reject-classic-auth required for unauth) |
| `/api/v1/passport\|user\|admin/**` | — | **404** hard cutover |

Auto-gen when empty: `/p|u|a/` + 12 `[a-z0-9]`. Must not conflict with each other, `FIXED_PUBLIC_CONFIG_PATH`, `subscribe_path`, or `server_api_prefix`.

**Plaintext allowlist (never under encrypted prefixes)**:

- `/api/v1/guest/payment/**` (payment notify)
- `/api/v1/guest/telegram/**` (bot webhook)
- Subscribe path from `site.subscribe_path` (client pull; `?token=`)

### 4. Validation & Error Matrix

| Condition | Result |
|-----------|--------|
| Classic panel path hit | HTTP 404 |
| Prefix conflicts `/config` on save | `BusinessException` 「…不能与固定公开配置路径 /config 冲突」 |
| Prefix conflicts subscribe/server prefix | `BusinessException` conflict message |
| Invalid prefix charset/shape | `BusinessException` 前缀不合法 |

### 5. Good/Base/Bad Cases

- Good: `GET {passport}/auth/login` rewrites to `/api/v1/passport/auth/login` + SM4 zone.
- Base: Empty prefixes on first boot → auto-gen persisted; public config returns them.
- Bad: Re-expose classic `/api/v1/user/**`; put payment notify under admin prefix.

### 6. Tests Required

- `ClientApiPathFilterTest` — rewrite + classic 404
- `ConfigServiceClientApiPrefixTest` — auto-gen, normalize, `/config` conflict

### 7. Wrong vs Correct

#### Wrong

```java
// Keep public mapping on /api/v1/passport/comm/config
```

#### Correct

```java
// PublicConfigRouteRegistrar → GET /config only; classic passport paths 404 via filter
```

---

## Scenario: Panel SM4 wire (body + X-A)

### 1. Scope / Trigger

- Trigger: Obfuscate panel JSON and JWT on encrypted zones; fail closed without `SM4_KEY`.
- Symptom if broken: plaintext ApiResponse on prefix paths; `Authorization: Bearer` accepted on rewrite zone; double encryption in controllers.

### 2. Signatures

```java
// PanelSm4Filter — order HIGHEST_PRECEDENCE+30 (after path rewrite)
// PanelSm4Support.ATTR_PANEL_SM4 / ATTR_REJECT_CLASSIC_AUTH / HEADER_X_A ("X-A")
// Sm4Util.encryptToEnvelope / decryptFromEnvelope / encryptToCompact / decryptFromCompact
// Env: SM4_KEY → v2board.sm4-key (16 UTF-8 bytes or 32 hex)
```

### 3. Contracts

| Direction | Format |
|-----------|--------|
| JSON body (POST/PUT/PATCH) | `{iv,payload}` base64 → plaintext JSON for controller |
| JSON response | Entire controller body (usually ApiResponse) → `{iv,payload}` |
| Auth | Header `X-A` = compact `base64url(iv).base64url(ct)` of JWT string |
| Classic auth on rewrite zone | `Authorization` / `?auth_data=` → **401** (`rejectClassicAuth`) |
| Empty body | Allowed (no decrypt) |
| `application/x-www-form-urlencoded` on SM4 zone | 400 「加密区仅接受 JSON 信封请求体」 |
| Non-JSON response (e.g. file) | Pass-through (no envelope) |

Key material is **panel** `SM4_KEY` / UI `VITE_SM4_KEY` — **not** `SHA-256(server_token)`.

Filter marks zones; controllers return plaintext `ApiResponse` (no nested encrypt).

### 4. Validation & Error Matrix

| Condition | Behavior |
|-----------|----------|
| `SM4_KEY` empty on SM4 zone | 500 plaintext `"SM4 key not configured"` |
| Invalid key | 500 `"SM4 key invalid: …"` |
| Body decrypt fail | 400 `"请求体解密失败"` / validation message |
| Response encrypt fail | 500 `"响应加密失败"` |
| Missing/bad `X-A` on protected rewrite routes | 401 |
| Classic Bearer on rewrite zone | 401 |

### 5. Good/Base/Bad Cases

- Good: UI sends envelope body + `X-A`; interceptor decrypts JWT; filter encrypts ApiResponse.
- Base: Dev key `0123456789abcdef` both sides.
- Bad: Controller wraps `data` with another envelope; accept Bearer on encrypted zone.

### 6. Tests Required

- `Sm4UtilTest` / `PanelSm4SupportTest` — envelope + compact round-trip
- Auth interceptor / controller tests covering `X-A` vs classic reject

### 7. Wrong vs Correct

#### Wrong

```java
return ApiResponse.success(Sm4Util.encryptToEnvelope(json, key));
// + Authorization: Bearer on rewritten path
```

#### Correct

```java
return ApiResponse.success(plainMap); // PanelSm4Filter outer-encrypts
// Client: X-A = encryptToCompact(jwt)
```

---

## Design Decision: Two SM4 key spaces

**Context**: Panel browser traffic vs node agent traffic must not share one secret.

**Decision**: Panel uses deploy env `SM4_KEY`; node derives from `server_token` / v2node `ApiKey`. Rotating one does not rotate the other.

---

## Design Decision: Rewrite keeps internal classic mappings

**Context**: Controllers and PHP-aligned paths stay `/api/v1/...` for maintainability.

**Decision**: `ClientApiPathFilter` rewrites external prefixes inward; classic external hits 404. Do not duplicate controller mappings per prefix.

---

## Common Mistake: Moving payment/telegram/subscribe under encrypted prefixes

**Symptom**: Gateway/payment bots or Clash clients fail (expect plaintext).

**Fix**: Keep guest payment/telegram and subscribe path outside panel SM4 zones; document in ops `docs/ops-panel-anti-block.md`.
