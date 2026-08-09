# Design — User + Passport API SM4

## Scope

| In | Out |
|----|-----|
| `/api/v1/passport/**`, `/api/v1/user/**` | Admin, node, payment notify, telegram, subscribe |
| `v2board-ui` user/passport calls | Admin UI (next child) |

## Path strategy

Config (under `site` or top-level client section — prefer `site`):

| Key | Replaces | Auto-gen |
|-----|----------|----------|
| `site.passport_api_prefix` | `/api/v1/passport` | `/p/` + 12 alnum |
| `site.user_api_prefix` | `/api/v1/user` | `/u/` + 12 alnum |

Wire: `{prefix}` + remainder of classic path after the replaced base.

Examples:

- `/api/v1/user/order/fetch` → `{user_api_prefix}/order/fetch`
- `/api/v1/passport/auth/login` → `{passport_api_prefix}/auth/login`

**Implementation**: `ClientApiPathFilter` (or HandlerMapping rewrite):

1. External hit on classic `/api/v1/user/**` or `/api/v1/passport/**` → **404** (hard cutover).
2. External hit on configured prefix → rewrite to classic internal path; controllers keep existing `@RequestMapping`.
3. Ensure + hot refresh prefixes on admin save (like subscribe/node).
4. Expose both prefixes inside **public** `passport/comm/config` SM4 payload so UI learns bases before login.

Validation: same path rules as `server_api_prefix` / subscribe; no conflict with node prefix, subscribe_path, secure_path, reserved `/api/v1/*`.

## SM4 (panel `SM4_KEY` / `VITE_SM4_KEY`)

Key: existing `Sm4Util.parseKey(sm4-key)` — **not** node token derive.

### Request

- **POST/PUT/PATCH** with JSON: body must be envelope `{iv,payload}`. Plaintext JSON = original body object (may be empty `{}`).
- **GET/DELETE**: no business body; optional empty.
- **Auth**: do **not** send `Authorization: Bearer …` or `?auth_data=` on the wire for these zones.
  - Header **`X-A`** = compact `base64url(iv).base64url(ct)` of UTF-8 JWT string (same compact form as node `e`).
  - `ClientAuthInterceptor` (+ user checkLogin helpers): read `X-A`, decrypt with SM4_KEY, treat as Bearer token; reject classic Authorization/auth_data on these routes when accessed via new prefix (after rewrite, still reject classic headers to avoid dual channel).

### Response

- Entire HTTP JSON body is one SM4 envelope `{iv,payload}` whose plaintext is the former response JSON (`ApiResponse` or raw map).
- **Special**: `GET …/comm/config` today returns `ApiResponse{data:{iv,payload}}` (inner public config). Change to: plaintext public config fields in `data`, **single outer** envelope for whole `ApiResponse` (avoid double SM4). Frontend: one decrypt → parse ApiResponse → use `data` fields (prefixes included).

### Fail closed

Missing/invalid `SM4_KEY` → encrypted zone returns 500/business error; no plaintext fallback.

## Frontend (`v2board-ui`)

- Extend `site.ts` / `http.ts`: after public config load, set `passportBase` / `userBase`.
- Rewrite all `/api/v1/passport` and `/api/v1/user` callers via helper `apiUrl('passport'|'user', path)`.
- Request interceptor: encrypt JSON body; set `X-A` from stored token; decrypt response envelope.
- `comm/config` bootstrap: still fetch via **new** passport prefix once known — chicken/egg: **bootstrap path**.

### Bootstrap chicken/egg

Public config is needed to learn prefixes, but config URL itself is under passport prefix.

**Lock**: Well-known bootstrap remains decryptable without path secrecy OR use fixed bootstrap:

Option chosen: keep a **single** bootstrap GET at obfuscated-but-configured path published only after first deploy… still needs initial URL.

Practical lock for MVP:

1. Env `VITE_PASSPORT_API_PREFIX` / `VITE_USER_API_PREFIX` optional overrides for first paint; **or**
2. Bootstrap `GET {app}/api/v1/passport/comm/config` temporarily — conflicts with hard cutover.

**Better lock**: Bootstrap endpoint stays at a **neutral fixed path** outside classic names, e.g. always `/c/cfg` (or from `site.public_config_path` auto-gen), registered like subscribe; returns outer SM4 envelope of public config **including** `passport_api_prefix` and `user_api_prefix`. Classic `/api/v1/passport/comm/config` removed.

Add config key `site.public_config_path` (auto `/c/`+8), document in ops. Frontend: `VITE_PUBLIC_CONFIG_PATH` optional; else try load from meta tag / default from build env matching server.

Simplest MVP aligned with “hard cutover”:

- Auto-gen `site.public_config_path`
- Put value into `index.html` via runtime is hard without SSR
- Frontend env: `VITE_PUBLIC_CONFIG_PATH` must match server (like SM4_KEY) — document required pair

**Decision**: `VITE_PUBLIC_CONFIG_PATH` + server `site.public_config_path` (ensure equal); fetch that for brand+prefixes; then all passport/user use returned prefixes.

## Admin UI

System config site tab (or security): show/edit/generate `passport_api_prefix`, `user_api_prefix`, `public_config_path`; help text for matching Vite env for public path.

## Tests

- Prefix ensure/validate; classic path 404; rewrite hits controller
- SM4 body + `X-A` auth roundtrip
- comm/config single envelope + prefixes in plaintext data
- guest payment/telegram still mapped classic

## Order note

Shared `PanelSm4Support` can be reused by admin child later.
