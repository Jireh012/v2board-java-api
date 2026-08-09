# Server Node Config (obfuscated node API / v2node)

> Panel↔node communication token, API prefix, SM4 wire, intervals, `base_config`, and v2node one-click install (Jireh012 Release + api-host).

**Upstream compare/merge**: [wyx2685/v2node](https://github.com/wyx2685/v2node) — see [v2node-upstream.md](./v2node-upstream.md). **Install/runtime**: [Jireh012/v2node](https://github.com/Jireh012/v2node).

---

## Scenario: Obfuscated node API paths + SM4

### 1. Scope / Trigger

- Trigger: Node pulls `{server_api_prefix}/{c|u|p|a|l}` (config/user/push/alive/alivelist).
- Symptom if broken: classic UniProxy / `/api/v2/server` still reachable; plaintext `token`/`node_id` accepted; body not enveloped.

### 2. Signatures

```java
// ConfigService
String ensureServerApiPrefix(); // auto-gen /n/+12 alnum when empty; persist
void save(...); // validate server_api_prefix; refresh NodeApiRouteRegistrar

// NodeSm4Codec
static byte[] deriveWorkingKey(String serverToken); // SHA-256(UTF-8)[0:16]
String encryptIdentityQuery(...); // compact e
NodeIdentity decryptIdentityQuery(String e, byte[] key);
Map<String,String> encryptBody(Object data, byte[] key);
```

Paths (registered by `NodeApiRouteRegistrar`):

| Action | Handler | Method |
|--------|---------|--------|
| `c` | config | GET |
| `u` | user | GET |
| `p` | push | POST |
| `a` | alive | POST |
| `l` | alivelist | GET |

### 3. Contracts

- **Prefix**: `server.server_api_prefix`; empty on admin fetch/save → auto-gen `/n/`+12 `[a-z0-9]`; hot-refresh on change. No classic `/api/v1/server/UniProxy/**` or `/api/v2/server/**` mappings.
- **Working key**: `SHA-256(UTF-8(server_token))[0:16]` — same string as 通讯密钥 / v2node `ApiKey`. **Not** public `SM4_KEY` / `VITE_SM4_KEY`. No separate `server_node_sm4_key`.
- **Query**: only `e` = `base64url(iv).base64url(ciphertext)` of `{"k":"<server_token>","i":<nodeId>,"t":"<code>"}` (`vn`=v2node). Reject plaintext `token`/`node_id`/`node_type`/`k`/`i`/`t`.
- **Body**: SM4 JSON envelope `{iv,payload}` for POST bodies and all success responses. No msgpack on new paths.
- **base_config** (inside decrypted business JSON): four ints as below.

| Response key | Config key | Default |
|--------------|------------|---------|
| `push_interval` | `server_push_interval` | 60 |
| `pull_interval` | `server_pull_interval` | 60 |
| `node_report_min_traffic` | `server_node_report_min_traffic` | 0 |
| `device_online_min_traffic` | `server_device_online_min_traffic` | 0 |

- Traffic thresholds enforced on the **node** side; panel does not re-filter push payload.
- `device_limit_mode` remains in alive handling only — not in `base_config`.

### 4. Validation & Error Matrix

| Condition | Result |
|-----------|--------|
| Configured `server_token` blank or length &lt; 16 | Auth fail `"token is error"` |
| Query `e` missing | `"token is null"` |
| Decrypt/`k` mismatch / plaintext identity params | `"token is error"` |
| `server_api_prefix` invalid on save | `BusinessException` 前缀不合法 |
| POST plaintext business body (no iv/payload) | 400 Invalid traffic/alive data |

### 5. Tests Required

- `ConfigServiceServerApiPrefixTest` — generate/validate/normalize prefix.
- `NodeSm4CodecTest` / `Sm4UtilTest` — key derive, compact `e`, body envelope.
- `UniProxyControllerTest` — `buildBaseConfig` four fields; token validity; `buildUserEntry`.
- `V2ServerControllerTest` — `base_config` + token helper (payload builder).

---

## Scenario: UniProxy user / alivelist (v2node)

### 1. Scope / Trigger

- Trigger: v2node pulls `{prefix}/u` and `{prefix}/l` with encrypted `e`.
- Symptom if broken: speed limits ignored on node; alivelist callable without identity.

### 2. Signatures

```java
// UniProxyController.user — each element of users[]
buildUserEntry(User): Map  // must include id, uuid, speed_limit, device_limit when non-null

// UniProxyController.alivelist
resolveNodeContext(request); // decrypt e + auth k
```

### 3. Contracts

- v2node `UserInfo` consumes `id`, `uuid`, `speed_limit`, `device_limit` (JSON snake_case inside envelope).
- Extra user columns allowed; panel omits nulls.
- `/l` response remains global `{alive: map[uid]count}` but **requires** valid `e` + existing node.
- v2node soft-fails on HTTP ≥399 for alivelist.

### 4. Validation & Error Matrix

| Condition | Result |
|-----------|--------|
| alivelist without `e` | `"token is null"` |
| alivelist bad `e` / missing node | `"token is error"` / `"server is not exist"` |

### 5. Tests Required

- `UniProxyControllerTest#buildUserEntry_includesV2nodeUserInfoFields`

---

## Scenario: Admin save `server` group

See [system-config.md](./system-config.md) — Scenario: Server node group validation (+ `server_api_prefix`).

---

## Design Decision: SM4 from 通讯密钥

**Context**: Avoid a second node secret in admin UI / install flags.

**Decision**: Derive working key from `server_token` / `ApiKey`. Rotating 通讯密钥 rotates SM4; nodes must update `ApiKey`.

**Related**: install_command uses `--api-key` + `--api-prefix` (no `--sm4-key`); binary/script source **must** be `Jireh012/v2node` (see install distribution scenario below).

**Not** panel `SM4_KEY` / UI `VITE_SM4_KEY` — see [panel-api-sm4.md](./panel-api-sm4.md).

---

## Scenario: v2node install distribution (Jireh012 release)

### 1. Scope / Trigger

- Trigger: Panel one-click install and `script/install.sh` download **both** the install script and the Linux zip from GitHub.
- Symptom if broken: script mentions `ApiPrefix` but binary is still upstream `wyx2685/v2node` → node cannot talk SM4 / obfuscated paths.
- Gotcha: pushing scripts to a fork is **not** enough; `install.sh` resolves `releases/latest` and downloads `v2node-linux-${arch}.zip`.

### 2. Signatures

```java
// AdminManageController — install_command template (v2node only)
"wget -N https://raw.githubusercontent.com/Jireh012/v2node/main/script/install.sh && bash install.sh --api-host %s --node-id %d --api-key %s --api-prefix %s"
```

```bash
# script/install.sh (Jireh012/v2node)
# latest: GET https://api.github.com/repos/Jireh012/v2node/releases/latest → tag_name
# asset:  https://github.com/Jireh012/v2node/releases/download/${tag}/v2node-linux-${arch}.zip
# manage: https://raw.githubusercontent.com/Jireh012/v2node/main/script/v2node.sh
```

Go module path may remain `github.com/wyx2685/v2node` (import / `-ldflags`); **download URLs must not**.

### 3. Contracts

| Piece | Canonical value |
|-------|-----------------|
| Panel `install_command` script host | `raw.githubusercontent.com/Jireh012/v2node/main/...` |
| Release owner/repo | `Jireh012/v2node` |
| Default branch for raw scripts | `main` (not `master`) |
| Asset names used by install | `v2node-linux-64.zip`, `v2node-linux-arm64-v8a.zip` (see `.github/build/friendly-filenames.json`) |
| Zip contents | `v2node` binary + `geoip.dat` + `geosite.dat` (+ README/LICENSE) |
| Flags written to `/etc/v2node/config.json` | `ApiHost`, `NodeID`, `ApiKey`, `ApiPrefix` |
| Forbidden download source | `wyx2685/v2node` releases or raw scripts for production install of this panel |

Publishing a new node feature that nodes must run: commit → push `main` → create/publish a GitHub Release (CI `.github/workflows/release.yml` on `release: published`, or upload matching zips). Empty Releases list → install fails at “检测版本失败”.

### 4. Validation & Error Matrix

| Condition | Result |
|-----------|--------|
| No GitHub Release / API rate limit | install.sh exits: 检测版本失败 |
| Wrong asset name for arch | download fail / unzip missing `v2node` |
| Binary without `ApiPrefix` / SM4 wire | node start or panel pull fails against obfuscated API |
| Panel still points at `wyx2685` | installs incompatible upstream binary |

### 5. Good / Base / Bad Cases

- **Good**: `releases/latest` = `v0.5.0` with `v2node-linux-64.zip`; binary strings contain `ApiPrefix`; config has `"ApiPrefix":"/n/...."`.
- **Base**: panel `install_command` uses Jireh012 raw `install.sh` + `--api-key` + `--api-prefix`.
- **Bad**: only fork scripts updated; Releases still empty or still served from `wyx2685/v2node`.

### 6. Tests Required

- Panel: assert `install_command` contains `Jireh012/v2node` and does **not** contain `wyx2685/v2node` (controller/list enrichment or snapshot).
- Ops smoke (manual/CI): `GET .../releases/latest` returns tag; zip lists `v2node`, `geoip.dat`, `geosite.dat`.
- Fork regression: after release, `strings` on binary includes `ApiPrefix` (or node e2e against `{prefix}/c`).

### 7. Wrong vs Correct

#### Wrong
```text
# Script from fork, binary from upstream — looks “fixed” but nodes lack SM4
wget .../Jireh012/v2node/.../install.sh
# install.sh internally → github.com/wyx2685/v2node/releases/...
```

#### Correct
```text
wget -N https://raw.githubusercontent.com/Jireh012/v2node/main/script/install.sh && bash install.sh \
  --api-host 'https://api.example.com' --node-id 1 --api-key '...' --api-prefix '/n/xxxxxxxxxxxx'
# install.sh → github.com/Jireh012/v2node/releases/download/<tag>/v2node-linux-64.zip
```

---

## Scenario: v2node install_command api-host

### 1. Scope / Trigger

- Trigger: Admin node editor shows one-click `install.sh`; empty `--api-host ''` breaks node install.
- Symptom if broken: `server_api_url` and `app_url` both blank → empty host; or Vite UI origin used as node API host.

### 2. Signatures

```java
// AdminManageController.addServersWithType (v2node)
apiHost = firstNonBlank(server_api_url, site.app_url, configService.resolveCurrentRequestOrigin())
```

### 3. Contracts

| Priority | Source |
|----------|--------|
| 1 | `server.server_api_url` |
| 2 | `site.app_url` |
| 3 | Current HTTP request origin (`X-Forwarded-*` / Host) |

`--api-host` must be the **panel API origin** reachable from the node (not the Vite/frontend origin).

UI may replace `--api-host ''` when displaying; local Vite ports `5173`/`5174`/`4173` → API `:8080`. Prefer setting `server_api_url` or `site.app_url` for public node installs.

### 4. Validation & Error Matrix

| Condition | Result |
|-----------|--------|
| All sources empty | `--api-host ''` (rare: no request context) |
| Config value `"null"` / blank | Treated as empty → fall through |
| Host = Vite UI only | Node cannot reach Spring API / SM4 routes |

### 5. Good / Base / Bad Cases

- **Good**: blank config + admin open via `https://panel.example` → `--api-host 'https://panel.example'`.
- **Base**: `server_api_url=https://api.example.com` → always that host in command.
- **Bad**: `String.valueOf(null)` → literal `"null"`; or `--api-host 'http://127.0.0.1:5173'`.

### 6. Tests Required

- `AdminManageControllerTest#configString_*` / `firstNonBlank_*`.
- UI display helper: Vite port remapped to `:8080` when filling empty host (if covered by FE tests).

### 7. Wrong vs Correct

#### Wrong
```bash
--api-host 'http://127.0.0.1:5173'   # frontend; node cannot use panel API
--api-host ''
```

#### Correct
```bash
--api-host 'http://127.0.0.1:8080'          # local Spring
--api-host 'https://api.example.com'       # production API / app_url
```
