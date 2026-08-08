# Design: 用户端安全模式访问守卫

## Boundaries

| Layer | Change |
|-------|--------|
| API | `ConfigService.getSafeModeEnable()`；`CommController#config` 增加 `safe_mode_enable` |
| UI | `site.ts` / `siteBrand`；`router.beforeEach` 用户端守卫 |
| Spec | `public-site-config.md`；UI `site-brand.md` |

## Contracts

Public config adds:

| Field | Type | Source |
|-------|------|--------|
| `safe_mode_enable` | 0/1 | `safe.safe_mode_enable` |

## Auth gate (UI)

```
PUBLIC = /login | /register | /forget
ADMIN = /admin/**

if path in ADMIN → existing admin guard
else if safeMode && !loggedIn && path not in PUBLIC
  → /login?redirect=<encoded original path>
else next()
```

`/` → `/dashboard` then gate applies (or gate before redirect; either OK).

## Compatibility

- Field additive on public config.
- When flag 0, no new redirects.
- Does not weaken `/api/v1/user/**` JWT.

## Rollback

Revert public field + router branch; admin toggle becomes inert again (current state).
