# Panel API Path Rewrite + SM4

> Passport / user / admin: configurable prefixes + action aliases + panel `SM4_KEY`.
> Payment notify: separate plaintext prefix (not SM4).
> Distinct from node SM4 (`server_token` derive) in [server-node.md](./server-node.md).
> Public bootstrap `GET /config`: [public-site-config.md](./public-site-config.md).

---

## Scenario: Client API prefix hard-cutover

### 1. Scope / Trigger

- Trigger: Replace classic `/api/v1/{passport|user|admin}` with `site.*_api_prefix` to cut V2Board path fingerprints.
- Symptom if broken: classic paths still 200; rewritten paths 404; prefixes collide with `/config` or subscribe/node/payment-notify prefixes.

### 2. Signatures

```java
// ConfigService
Map<String,String> ensureClientApiPaths(); // auto-gen empty prefixes; persist
// keys: passport_api_prefix, user_api_prefix, admin_api_prefix,
//       payment_notify_prefix, public_config_path(="/config")

static final String FIXED_PUBLIC_CONFIG_PATH = "/config";

// ClientApiPathRegistry — hot cache; refresh on save / ensure
// ClientApiPathFilter — order HIGHEST_PRECEDENCE+20
```

Internal controllers keep `@RequestMapping("/api/v1/...")`; filter rewrites before dispatch.

### 3. Contracts

| External path | Internal mapping | Notes |
|---------------|------------------|-------|
| `{passport_api_prefix}/{alias}` | `/api/v1/passport/{classicRel}` | Alias + Panel SM4 (see scenarios below) |
| `{user_api_prefix}/{alias}` | `/api/v1/user/{classicRel}` | same |
| `{admin_api_prefix}/{alias}` | `/api/v1/admin/{classicRel}` | same |
| `{payment_notify_prefix}/{method}/{uuid}` | `/api/v1/guest/payment/notify/...` | Plaintext; no SM4 |
| `GET /config` | public config handler | Panel SM4 response only |
| `/api/v1/passport\|user\|admin/**` | — | **404** |
| `/api/v1/guest/payment/**` | — | **404** |
| `{panelPrefix}/{classicRel}` e.g. `…/getSubscribe` | — | **404** |

Auto-gen when empty: `/p|u|a|g/` + 12 `[a-z0-9]`. Must not mutually conflict, nor with `FIXED_PUBLIC_CONFIG_PATH`, `subscribe_path`, `server_api_prefix`.

**Still classic plaintext (until follow-up)**: `/api/v1/guest/telegram/**`, subscribe path.

### 4. Validation & Error Matrix

| Condition | Result |
|-----------|--------|
| Classic panel or guest-payment path | HTTP 404 |
| Prefix conflicts `/config` / subscribe / server / sibling prefixes | `BusinessException` |
| Invalid prefix charset/shape | `BusinessException` 前缀不合法 |

### 5. Good/Base/Bad Cases

- Good: `GET {passport}/{alias(auth/login)}` → internal classic + SM4 zone.
- Base: Empty prefixes on first boot → auto-gen persisted.
- Bad: Re-expose classic `/api/v1/user/**`; put payment notify under encrypted panel prefix.

### 6. Tests Required

- `ClientApiPathFilterTest` — panel alias + guest payment rewrite/404
- `ConfigServiceClientApiPrefixTest` — auto-gen incl. `payment_notify_prefix`
- `PanelApiActionAliasesTest` — derive/resolve

### 7. Wrong vs Correct

#### Wrong

```java
// Public mapping still on /api/v1/passport/comm/config
```

#### Correct

```java
// PublicConfigRouteRegistrar → GET /config only; classic passport paths 404 via filter
```

---

## Scenario: Action-name aliases (SM4_KEY derived)

### 1. Scope / Trigger

- Trigger: Classic action segments (`getSubscribe`, `fetch`, `info`, …) remain fingerprints after prefix rewrite.
- Symptom if broken: DevTools shows `{prefix}/getSubscribe`; classic remainder still 200.

### 2. Signatures

```java
// PanelApiActionCatalog.all() — exhaustive classicRel per zone
// PanelApiActionAliases
static String normalizeClassicRel(String path);
static String deriveAlias(String sm4Key, String zone, String classicRel);
String resolveClassicRel(String zone, String aliasSegment); // null if unknown

// ClientApiPathFilter — after panel prefix match: single-segment alias only; else 404
```

### 3. Contracts

| Item | Rule |
|------|------|
| `classicRel` | No leading/trailing `/` (e.g. `order/fetch`, `server/vmess/save`) |
| `alias` | `hex(SHA-256(UTF-8(SM4_KEY) \|\| 0x00 \|\| zone \|\| 0x00 \|\| classicRel))[0:12]` |
| External | `{prefix}/{alias}` — **one** opaque segment |
| Internal | `/api/v1/{zone}/{classicRel}` (controllers unchanged; not publicly reachable) |
| Catalog | Every panel handler; add entry when adding endpoints |
| `/config` | Not aliased |
| Key | Panel `SM4_KEY` / `VITE_SM4_KEY` — not `server_token` |

Locked vector (dev key `0123456789abcdef`): `user` + `getSubscribe` → `59327a5e63c5`.

### 4. Validation & Error Matrix

| Condition | Result |
|-----------|--------|
| `{prefix}/getSubscribe` (classic remainder) | 404 |
| Unknown alias / multi-segment remainder | 404 |
| Alias collision at startup | Fail fast |
| Empty `SM4_KEY` | Alias map still builds; SM4 filter fails closed on zone |

### 5. Good/Base/Bad Cases

- Good: UI `apiUrl('user','/getSubscribe')` → `{userPrefix}/59327a5e63c5` (dev key).
- Base: Dev key `0123456789abcdef` on both sides.
- Bad: Pass classic remainder through filter; omit new endpoint from catalog.

### 6. Tests Required

- `PanelApiActionAliasesTest` — locked vector + catalog round-trip
- `ClientApiPathFilterTest` — alias hit / classic remainder 404

### 7. Wrong vs Correct

#### Wrong

```java
return classicBase + path.substring(prefix.length()); // leaks getSubscribe
```

#### Correct

```java
// remainder must be alias; PanelApiActionAliases.resolveClassicRel → classicRel
```

---

## Scenario: Payment notify path prefix (plaintext)

### 1. Scope / Trigger

- Trigger: Classic `/api/v1/guest/payment/notify/...` is a strong V2Board fingerprint; payment gateways require **plaintext** bodies.
- Symptom if broken: gateway 404; notify SM4-wrapped; classic path still live; admin `notify_url` still classic.

### 2. Signatures

```java
// ConfigService
String ensurePaymentNotifyPrefix(); // site.payment_notify_prefix; empty → /g/+12
String buildPaymentNotifyPath(method, uuid); // {prefix}/{method}/{uuid}

// ClientApiPathFilter (no ATTR_PANEL_SM4)
isClassicGuestPayment(path) → 404
rewritePaymentNotify(path, prefix) → /api/v1/guest/payment/notify/{method}/{uuid}

// Consumers
PaymentService.buildNotifyUrl → buildPaymentNotifyPath + notify_domain / app_url
AdminPaymentController.fetch → same for notify_url field
```

### 3. Contracts

| Item | Rule |
|------|------|
| External | `{payment_notify_prefix}/{method}/{uuid}` exactly **two** segments after prefix |
| Internal | `/api/v1/guest/payment/notify/{method}/{uuid}` |
| Classic | `/api/v1/guest/payment/**` → **404** |
| Body / auth | Plaintext; no `X-A`; no Panel SM4 |
| Admin UI | `site.payment_notify_prefix` in system config; copy `notify_url` from payments list |
| Vite / reverse proxy | Forward `/g/` (or custom prefix) to API |

### 4. Validation & Error Matrix

| Condition | Result |
|-----------|--------|
| Prefix conflicts panel/subscribe/server/`/config` | `BusinessException` |
| Wrong segment count under prefix | 404 |
| Classic guest payment path | 404 |
| Prefix empty on ensure | Auto-gen `/g/`+12 and persist |

### 5. Good/Base/Bad Cases

- Good: `POST {prefix}/AlipayF2F/{uuid}` → controller plaintext (`gate is not enable` if unknown).
- Base: Auto-gen `/g/`+12 on first `ensureClientApiPaths`.
- Bad: SM4-wrap notify; leave classic path open; hard-code `/api/v1/guest/payment/notify` in `buildNotifyUrl`.

### 6. Tests Required

- `ClientApiPathFilterTest#rewritePaymentNotify_*` / `isClassicGuestPayment_*`
- `ConfigServiceClientApiPrefixTest` — `payment_notify_prefix` auto-gen pattern `^/g/[a-z0-9]{12}$`
- Manual/ops: after prefix change, re-check gateway callback URLs

### 7. Wrong vs Correct

#### Wrong

```java
String path = "/api/v1/guest/payment/notify/" + method + "/" + uuid;
request.setAttribute(PanelSm4Support.ATTR_PANEL_SM4, true); // on notify
```

#### Correct

```java
String path = configService.buildPaymentNotifyPath(method, uuid);
// Filter rewrites {prefix}/method/uuid → classic internal; no Panel SM4 mark
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
| Classic auth on rewrite zone | `Authorization` / `?auth_data=` → **401** |
| Empty body | Allowed (no decrypt) |
| `application/x-www-form-urlencoded` on SM4 zone | 400 「加密区仅接受 JSON 信封请求体」 |
| Non-JSON response | Pass-through |
| Payment notify / telegram / subscribe | **Never** marked panel SM4 |

Key material is **panel** `SM4_KEY` / UI `VITE_SM4_KEY` — **not** `SHA-256(server_token)`.

### 4. Validation & Error Matrix

| Condition | Behavior |
|-----------|----------|
| `SM4_KEY` empty on SM4 zone | 500 plaintext `"SM4 key not configured"` |
| Invalid key | 500 `"SM4 key invalid: …"` |
| Body decrypt fail | 400 |
| Response encrypt fail | 500 `"响应加密失败"` |
| Missing/bad `X-A` on protected rewrite routes | 401 |
| Classic Bearer on rewrite zone | 401 |

### 5. Good/Base/Bad Cases

- Good: UI envelope body + `X-A`; filter encrypts ApiResponse.
- Base: Dev key `0123456789abcdef`.
- Bad: Controller double-encrypts; accept Bearer on encrypted zone.

### 6. Tests Required

- `Sm4UtilTest` / `PanelSm4SupportTest`
- Auth interceptor / controller tests for `X-A` vs classic reject

### 7. Wrong vs Correct

#### Wrong

```java
return ApiResponse.success(Sm4Util.encryptToEnvelope(json, key));
```

#### Correct

```java
return ApiResponse.success(plainMap); // PanelSm4Filter outer-encrypts
```

---

## Design Decision: Two SM4 key spaces

**Context**: Panel browser traffic vs node agent traffic must not share one secret.

**Decision**: Panel uses deploy env `SM4_KEY`; node derives from `server_token` / v2node `ApiKey`.

---

## Design Decision: Rewrite keeps internal classic mappings

**Context**: Controllers stay `/api/v1/...` for PHP functional alignment.

**Decision**: Filter rewrites `{prefix}/{alias}` (and payment `{prefix}/{method}/{uuid}`) inward; external classic paths 404. Do not put aliases on `@RequestMapping`.

---

## Design Decision: Single-segment action aliases from SM4_KEY

**Context**: FE/BE sync without shipping a full action map in `/config`.

**Decision**: Derive 12-hex aliases from panel `SM4_KEY` + zone + classicRel. Rotating `SM4_KEY` rotates action names site-wide.

---

## Design Decision: Payment path obfuscation without SM4

**Context**: Gateways cannot send SM4 envelopes; path still fingerprints V2Board.

**Decision**: Configurable `payment_notify_prefix` + hard-cut classic guest payment path; body remains plaintext.

---

## Common Mistake: Moving payment/telegram/subscribe under Panel SM4

**Symptom**: Gateways / Clash clients fail.

**Fix**: Payment uses plaintext `{payment_notify_prefix}/...` only; telegram/subscribe stay outside panel SM4. See `docs/ops-panel-anti-block.md`.

---

## Common Mistake: Forgetting PanelApiActionCatalog entry

**Symptom**: New endpoint works in controller tests but UI gets 404 on aliased URL.

**Fix**: Add classicRel to `PanelApiActionCatalog` when adding passport/user/admin handlers.
