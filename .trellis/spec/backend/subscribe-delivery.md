# Subscribe Delivery Contracts

> Executable contracts for user subscribe link generation and node naming in client output.

---

## Scenario: Resolve `subscribe_url` base (DB → request origin)

### 1. Scope / Trigger

- Trigger: `getSubscribe` / knowledge templates / protocol builders must emit a **usable absolute** subscribe URL when possible.
- Cross-layer: `v2_system_config` (site) → `ConfigService` → `Helper.getSubscribeUrl` → UI copy / VPN clients.
- Writing only `@Value("${v2board.subscribe-url}")` ignores admin DB edits and leaves relative paths.

### 2. Signatures

**ConfigService** (`com.v2board.api.service.ConfigService`):

```java
String getConfiguredSubscribeUrlBase(); // DB/yml only, no request
String getSubscribeUrlBase();           // configured, else request origin
String getSubscribePath();              // DB site.subscribe_path → yml → default
String resolveCurrentRequestOrigin();   // scheme://host[:port] from current request
String buildSubscribeUrl(String token, Long userId); // always subMethod=0 (direct token)
boolean getShowInfoToServerEnable();                  // DB subscribe.show_info_to_server_enable
int getShowSubscribeMethod();                         // 0=all / 1=expire-only / 2=traffic-only (info nodes)
int getShowSubscribeExpire();                         // days for UI「即将到期」; not used in URL/TOTP
```

**User API**

| Method | Path | Field |
|--------|------|-------|
| GET | `/api/v1/user/getSubscribe` | `data.subscribe_url` |
| GET | `/api/v1/user/getSubscribe` | `data.show_subscribe_expire` |

**Default path**: `/api/v1/client/subscribe`. HTTP route + token interceptor follow DB `site.subscribe_path` via `SubscribeRouteRegistrar` / `ClientTokenInterceptor` (admin save hot-reloads; no restart).

### 3. Contracts

**Base URL priority** (first non-empty wins):

1. DB `site.subscribe_url` (comma and/or newline separated; trim trailing `/`)
2. DB `site.app_url`
3. yml `v2board.subscribe-url` / `v2board.app-url`
4. Current HTTP request origin (`Host` / `X-Forwarded-Host` + `X-Forwarded-Proto`; `site.force_https=1` forces `https`)

**Path**: DB `site.subscribe_path` → yml → `/api/v1/client/subscribe`.

**Response example**

```json
{
  "code": 0,
  "data": {
    "subscribe_url": "http://192.168.1.10:8080/api/v1/client/subscribe?token=<token>"
  }
}
```

When no config and no request context, `Helper.getSubscribeUrl` may still return a relative path — UI must absolute-ize (see frontend component guidelines).

### 4. Validation & Error Matrix

| Condition | Result |
|-----------|--------|
| Empty `subscribe_url` + empty `app_url` + in-request | Use request origin |
| Empty everything + no request attributes | Relative path only |
| `force_https=1` | Scheme forced to `https` even if request is `http` |
| Custom `subscribe_path` saved in admin | Link path + HTTP route + token gate update immediately (`SubscribeRouteRegistrar.refresh`) |

### 5. Good / Base / Bad Cases

- **Good**: Admin sets `subscribe_url=https://panel.example.com` → all clients get that host.
- **Base**: Dev leaves both empty → `http://localhost:8080/...` or LAN Host from API request.
- **Bad**: Controllers keep private `@Value` for subscribe URL and ignore `ConfigService` after admin save.

### 6. Tests Required

- Unit: `getSubscribeUrlBase` preference order (subscribe_url → app_url → yml).
- Unit: `resolveCurrentRequestOrigin` with `Host: 192.168.1.10:8080` and `X-Forwarded-Proto: https`.
- Integration: `GET /api/v1/user/getSubscribe` with empty DB site URLs returns absolute URL including request host.
- Assertion: response `subscribe_url` matches `^https?://`.

### 7. Wrong vs Correct

#### Wrong

```java
@Value("${v2board.subscribe-url:}")
private String subscribeUrlConfig;
// ...
Helper.getSubscribeUrl(token, id, method, path, subscribeUrlConfig, expire);
```

#### Correct

```java
@Autowired ConfigService configService;
// ...
configService.buildSubscribeUrl(user.getToken(), user.getId());
```

---

## Scenario: Panel vs external node name markers

### 1. Scope / Trigger

- Trigger: Clients must distinguish **panel-owned** vs **third-party** nodes in subscribe output.
- Cross-layer: `ClientController` markers → Clash / Sing-box / share URI consumers.

### 2. Signatures

**ClientController** (before merge / handlers):

```java
applyNodeSecurityMarkers(panelServers, "🔒 ");
applyNodeSecurityMarkers(externalServers, "⚠️ ");
```

Prefixes also rewrite:

- `server.name`
- `clash_proxy.name`
- `singbox_outbound.tag`
- `share_uri` `#` remark (URL-encoded)

### 3. Contracts

| Source | Prefix | `type` / flag |
|--------|--------|----------------|
| Panel (`ServerService.getAvailableServers`) | `🔒 ` | normal protocol types |
| External (`ExternalSubscribeNodeService.listReachableAsServerMaps`) | `⚠️ ` | `type=external`, `external=true`; list is already logical-key deduped + same-name numbered (`name1`/`name2`) before markers — see [external-subscribe.md](./external-subscribe.md) |

Info nodes injected by `setSubscribeInfoToServers` are added **after** marking and stay unmarked.

**Display config (decoupled from URL)**:

| Key | Effect |
|-----|--------|
| `subscribe.show_info_to_server_enable` | Gate: when off, no info nodes |
| `subscribe.show_subscribe_method` | 0=traffic+reset+expire; 1=expire only; 2=traffic only |
| `subscribe.show_subscribe_expire` | User UI badge days; **not** passed to `Helper.getSubscribeUrl` |

`buildSubscribeUrl` always emits `?token=<plain>` (method 0).

### 4. Validation & Error Matrix

| Condition | Result |
|-----------|--------|
| Name already starts with `🔒 ` or `⚠️ ` | Do not double-prefix |
| Empty name | Skip |
| Clash builder keeps original `clash_proxy.name` | **Bug** — client shows unmarked third-party names |

### 5. Good / Base / Bad Cases

- **Good**: Clash Meta list shows `🔒 US_California` and `⚠️ AT 奥地利 ...`.
- **Base**: General base64 URI fragment uses marked name via rewritten `share_uri`.
- **Bad**: Only set `server.name`, leave `clash_proxy.name` / outbound `tag` / URI `#` as original.

### 6. Tests Required

- Unit: after markers, `clash_proxy.name` and `singbox_outbound.tag` equal marked `server.name`.
- Unit: `ClashMetaBuilder` output proxy `name` equals marked name even when `clash_proxy` already had a name.
- Assertion: YAML/JSON proxy list contains `⚠️` for at least one external fixture.

### 7. Wrong vs Correct

#### Wrong

```java
// ClashMetaBuilder — only fill name when missing
if (externalProxy.get("name") == null && server.get("name") != null) {
    externalProxy.put("name", server.get("name"));
}
```

#### Correct

```java
externalProxy = new LinkedHashMap<>(externalProxy);
if (server.get("name") != null) {
    externalProxy.put("name", server.get("name")); // always use marked outer name
}
```

Same for Sing-box: always set `outbound.tag` from `server.get("name")` when present.

---

## Scenario: Hot-reload subscribe HTTP path

### 1. Scope / Trigger

- Trigger: Admin changes `site.subscribe_path` and expects the new path to accept GET subscribe **without process restart**.
- Cross-layer: `ConfigService.save` → `SubscribeRouteRegistrar.refresh` + `ClientTokenInterceptor` path match.

### 2. Signatures

```java
// SubscribeRouteRegistrar
void refresh(); // unregister old RequestMappingInfo, register GET path from ConfigService.getSubscribePath()
static String normalizePath(String path);

// ConfigService.save — when body contains "site"
subscribeRouteRegistrar.refresh();
```

Token gate: `ClientTokenInterceptor` registered on `/**`, early-returns unless URI equals current `getSubscribePath()`.

### 3. Contracts

| Piece | Behavior |
|-------|----------|
| Default path | `/api/v1/client/subscribe` |
| Storage | DB `site.subscribe_url` may be comma **or** newline separated; `normalizeSubscribeBases` splits `[,\\n\\r]+`, persists normalized comma list for bases |
| Path change | Old path → 404; new path → token interceptor + `ClientController.subscribe` |
| Boot | `ApplicationRunner` registers path from DB/yml once |

### 4. Validation & Error Matrix

| Condition | Result |
|-----------|--------|
| Path unchanged on save | `refresh` no-op |
| Empty path | Normalize to default |
| Interceptor on non-subscribe URI | `preHandle` returns true (pass-through) |
| Subscribe without `token` | 403 `token is null` |
| Save `site.subscribe_path` empty/blank | Allowed; runtime `getSubscribePath()` → default |
| Save non-empty path | `ConfigService.validateSubscribePathInSaveBody`：trim、补前导 `/`、去尾 `/`；须匹配 `^/[A-Za-z0-9._~/-]+$`，无 `..`，长度 ≤128；不得与 `/api/v1/user|admin|passport|guest|server` 前缀或当前 `secure_path` 段冲突 |
| Illegal / conflicting path on save | `BusinessException(500, "订阅路径不合法：…")`，拒绝写入 |

运维藏源站 / 域名切换基线见仓库 [`docs/ops-panel-anti-block.md`](../../../docs/ops-panel-anti-block.md)。

### 5. Good/Base/Bad Cases

- Good: Save path `/s` → `GET /s?token=…` works; old `/api/v1/client/subscribe` 404.
- Base: Default path after boot.
- Bad: Register route from `@Value` only at startup; interceptor still bound to old path.

### 6. Tests Required

- Unit: `SubscribeRouteRegistrarTest.normalizePath_*` + refresh unregisters previous mapping
- Unit: `ConfigServiceSubscribePathValidationTest` — empty OK; illegal/`..`/reserved prefix/`secure_path` collision rejected; valid custom normalized
- Manual/integration: save custom path in admin → curl new path 403 without token, 200 with valid token; default path 404

### 7. Wrong vs Correct

#### Wrong

```java
@Value("${v2board.subscribe-path:}")
private String subscribePath;
// register once in ContextRefreshedEvent — DB edits ignored until restart
registry.addInterceptor(token).addPathPatterns(subscribePath);
```

#### Correct

```java
// Route: ConfigService.getSubscribePath() + registerMapping / unregisterMapping
// Interceptor: match request URI to getSubscribePath() at runtime
```

---

## Design Decision: Request-origin fallback

**Context**: Local/LAN installs often leave `app_url` / `subscribe_url` empty.

**Options**: Relative path only · Require admin config · Fall back to current request Host.

**Decision**: Fall back to request origin so copy/import works on localhost and LAN without config; still prefer explicit DB URLs in production.

**Gotcha**: Vite `changeOrigin: true` makes the API see the **backend** Host (e.g. `localhost:8080`), not the browser origin. Frontend must still absolute-ize relative URLs with `window.location.origin` when needed.
