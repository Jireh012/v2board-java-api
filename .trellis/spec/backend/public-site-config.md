# Public Site Config

> Unauthenticated brand config for UI rendering (`app_name`).

---

## Scenario: Public passport comm config

### 1. Scope / Trigger

- Trigger: Frontend must render `site.app_name` on login pages (no JWT) and shared chrome.
- Do **not** expose `/api/v1/admin/config/fetch` publicly — it returns full nested config including secrets.

### 2. Signatures

- `GET /api/v1/passport/comm/config`
- Controller: `CommController#config`
- Source of truth: `ConfigService.getAppName()` → `site.app_name` (DB / PHP flat / yml default `V2Board`)

### 3. Contracts

**Request**: none (no auth header required). Path is outside `ClientAuthInterceptor` (`/api/v1/user/**`, `/api/v1/admin/**` only).

**Response** (`ApiResponse`, snake_case `data`):

| Field | Type | Notes |
|-------|------|-------|
| `app_name` | string | Non-empty; never null in practice |
| `stop_register` | int 0/1 | `1` = registration closed |
| `invite_force` | int 0/1 | `1` = invite code required |

Adding more public keys later must stay non-sensitive (no SMTP password, `server_token`, bot token, etc.).

### 4. Validation & Error Matrix

| Condition | Behavior |
|-----------|----------|
| Config load failure inside `getAppName` | Falls back to yml / `"V2Board"`; endpoint still `code=0` |
| Auth missing | Still success (public) |

### 5. Good/Base/Bad Cases

- Good: DB `site.app_name = "AcmeVPN"` → `data.app_name = "AcmeVPN"`
- Base: empty DB → `"V2Board"`
- Bad: returning full `getFullConfig()` or nesting `site` secrets

### 6. Tests Required

- Unit: `CommControllerConfigTest` — `app_name` / `stop_register` / `invite_force` present; no other keys
- Optional: assert `getAppName` prefers stored/PHP value over yml (`ConfigServiceAppNameTest`)

### 7. Wrong vs Correct

#### Wrong

```java
return ApiResponse.success(configService.getFullConfig()); // leaks email/server/telegram secrets
```

#### Correct

```java
Map<String, Object> data = new LinkedHashMap<>();
data.put("app_name", configService.getAppName());
data.put("stop_register", configService.getStopRegister());
data.put("invite_force", configService.getInviteForce());
return ApiResponse.success(data);
```

---

## Design Decision: Mount on passport CommController

**Context**: Login pages cannot call admin config APIs.

**Options**: `guest/comm/config` vs extend `passport/comm`.

**Decision**: `GET /api/v1/passport/comm/config` on existing `CommController` — already unauthenticated and used by passport flows.

**Related**: Frontend `v2board-ui` `src/api/site.ts` + `src/siteBrand.ts` (`auth: false`, localStorage `v2board_app_name`, fallback `V2Board`).
