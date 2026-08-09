# Design — Admin API SM4

## Scope

Reuse user-child building blocks: `ClientApiPathFilter`, `PanelSm4Filter`, `X-A`, `SM4_KEY`.

| In | Out |
|----|-----|
| `/api/v1/admin/**` | Node, user/passport (done), guest payment/telegram, subscribe |
| Admin UI API calls | UI `secure_path` page route (already separate) |

## Path

- Config: `site.admin_api_prefix` replaces `/api/v1/admin`
- Auto-gen: `/a/` + 12 alnum when empty
- Wire: `{admin_api_prefix}` + relative (e.g. `/config/fetch`)
- Classic `/api/v1/admin/**` → **404**
- Prefix rewrite → internal classic mappings (controllers unchanged)
- Expose `admin_api_prefix` in **public config** `data` (same envelope as user child) so admin login can resolve API base before auth
- Conflict checks vs passport/user/public/node/subscribe/secure_path

## SM4 / Auth

- Same as user zone: body envelope, response envelope, `X-A` for JWT
- `PanelSm4Filter` / path filter mark admin rewrite as encrypted zone
- `ClientAuthInterceptor` already on `/api/v1/admin/**` after rewrite — prefer `X-A`, reject classic Authorization on encrypted zone
- Admin login (`POST …/login`) JSON + SM4 like passport login

## Frontend

- `paths.ts`: `apiUrl('admin', …)` using `admin_api_prefix` from public config
- All `/api/v1/admin/**` callers → helper (or http layer rewrite)
- `isPanelEncryptedUrl` includes admin prefix
- System config: edit/generate `admin_api_prefix`

## Tests

- Prefix ensure/validate; classic admin 404; rewrite; SM4 + X-A; guest payment untouched
