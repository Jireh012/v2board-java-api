# Design: 用户注册页与注册开关

## API

Extend `GET /api/v1/passport/comm/config` `data`:

```json
{
  "app_name": "…",
  "stop_register": 0,
  "invite_force": 0
}
```

Read via `ConfigService.getFullConfig()` site/invite sections; coerce to int 0/1.

`POST /api/v1/passport/auth/register` body (JSON, existing):

```json
{ "email": "", "password": "", "invite_code": "" }
```

Frontend: `auth: false`.

## Frontend

| Piece | Role |
|-------|------|
| `siteBrand.ts` / `api/site.ts` | Extend public config types; expose `stopRegister`, `inviteForce` refs |
| `RegisterView.vue` | New page, reuse `login.css` |
| `LoginView.vue` | Conditional link when `!stopRegister` |
| `router.ts` | `/register` |
| `api/auth.ts` | `register(...)` |

Closed gate: if `stop_register===1` after load, show message + link to `/login`.

Invite: `route.query.code` → invite field; `pv` call optional (existing passport/comm/pv) — nice-to-have, not required for AC.

## Compatibility

- Extending public config is additive for existing `app_name` consumers.
- Update `public-site-config.md` / `site-brand.md` on finish.
