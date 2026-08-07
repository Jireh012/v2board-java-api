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
String buildSubscribeUrl(String token, Long userId);
```

**User API**

| Method | Path | Field |
|--------|------|-------|
| GET | `/api/v1/user/getSubscribe` | `data.subscribe_url` |

**Default path**: `/api/v1/client/subscribe` (route registration still uses boot-time `v2board.subscribe-path` / `SUBSCRIBE_PATH`).

### 3. Contracts

**Base URL priority** (first non-empty wins):

1. DB `site.subscribe_url` (comma-separated allowed; trim trailing `/`)
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
| Custom `subscribe_path` in DB only | Link path changes; **HTTP route still needs env/yml + restart** |

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
| External (`ExternalSubscribeNodeService.listReachableAsServerMaps`) | `⚠️ ` | `type=external`, `external=true` |

Info nodes injected by `setSubscribeInfoToServers` are added **after** marking and stay unmarked.

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

## Design Decision: Request-origin fallback

**Context**: Local/LAN installs often leave `app_url` / `subscribe_url` empty.

**Options**: Relative path only · Require admin config · Fall back to current request Host.

**Decision**: Fall back to request origin so copy/import works on localhost and LAN without config; still prefer explicit DB URLs in production.

**Gotcha**: Vite `changeOrigin: true` makes the API see the **backend** Host (e.g. `localhost:8080`), not the browser origin. Frontend must still absolute-ize relative URLs with `window.location.origin` when needed.
