# Server Node Config (obfuscated node API / v2node)

> Panel↔node communication token, API prefix, SM4 wire, intervals, and `base_config` contract.

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

**Related**: install_command uses `--api-key` + `--api-prefix` (no `--sm4-key`); source `Jireh012/v2node`.
