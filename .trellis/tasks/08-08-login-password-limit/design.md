# Design: password error limit on user login

## Boundaries

| Layer | Change |
|-------|--------|
| API | `AuthController#login`（或抽到小 helper/service）读写 `NodeCacheService` + `ConfigService` |
| UI | `AdminSystemConfigView` 文案：同邮箱 / 非 IP |

## Algorithm（PHP parity）

```
if password_limit_enable == 1:
  key = PASSWORD_ERROR_LIMIT_{email}
  if get(key) >= password_limit_count:
    throw "… after {password_limit_expire} minutes."
find user; if null → incorrect (no increment)
if password wrong:
  if enable: set(key, count+1, Duration.ofMinutes(expire))
  throw incorrect
continue success (do not delete key)
```

Use existing `CacheKeyUtil.get("PASSWORD_ERROR_LIMIT", email)` + `NodeCacheService` (DB1 / PHP serialize) like register IP limit.

## Config readers

Prefer `ConfigService` getters or `intFromGroup("safe", …)` — avoid flat top-level keys.

## Tests

Unit: mock `NodeCacheService` / `ConfigService` around login helper or controller — locked / increment / disabled / unknown email.
