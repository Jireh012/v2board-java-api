# Design — Node API path obfuscation + SM4

## Boundaries

| Repo | Responsibility |
|------|----------------|
| `v2board-java-api` | Prefix config, dynamic routes, SM4 from `server_token`, remove classic mappings, install_command |
| `v2board-ui` | Admin server tab: API prefix only (+ help on 通讯密钥) |
| `v2node` | `ApiPrefix`; SM4 from `ApiKey`; client wire; install.sh `--api-prefix` |

PHP upstream is **not** the wire target (intentional fork). Business payloads inside envelopes still follow Java↔v2node field contracts.

## Key derivation

```
workingKey[16] = SHA-256( UTF-8(server_token | ApiKey) )[0:16]
```

- Same string as 通讯密钥 / `--api-key` / `ApiKey`.
- Do **not** store a second SM4 secret.
- `Sm4Util.encrypt/decrypt` use `workingKey` (raw 16 bytes), not `parseKey(token)` (token length ≠ 16/32hex).

## Wire contract

### Paths

`{server_api_prefix}/{action}` → `c`/`u`/`p`/`a`/`l` (config/user/push/alive/alivelist).

Prefix: normalize like subscribe_path; auto-gen `/n/`+12 `[a-z0-9]`; reject reserved conflicts.

### Query `e`

1. Plaintext: `{"k":"<server_token>","i":<nodeId>,"t":"<typeCode>"}` (`vn`=v2node)
2. Encrypt with `workingKey` → compact `base64url(iv).base64url(payload)`
3. No plaintext `token`/`node_id`/`node_type`/`k`/`i`/`t` on new routes

Panel: derive key from configured `server_token` → decrypt `e` → verify `k` equals `server_token` → resolve node.

### Body SM4

- Same `workingKey`; JSON `{iv,payload}` for POST body + all success responses
- No msgpack on new paths

```mermaid
sequenceDiagram
  participant N as v2node
  participant P as Java panel
  N->>N: key=SHA256(ApiKey)[:16]
  N->>P: GET {prefix}/u?e=...
  P->>P: key=SHA256(server_token)[:16]; decrypt e; auth k
  P->>N: body envelope users
```

## Java architecture

1. Strip classic `@RequestMapping` from UniProxy / V2Server; register via `NodeApiRouteRegistrar`.
2. `NodeSm4Codec`: derive key; decode `e`; body encrypt/decrypt.
3. `ConfigService`: `server_api_prefix` validate + auto-gen on fetch/save; **no** `server_node_sm4_key`.
4. `NodeTypeCodes` for `t`.
5. Fail closed if token/prefix missing.

## v2node

- `NodeConfig.ApiPrefix` only (no `Sm4Key`)
- Client: derive key from `Key`/`ApiKey`; build `e`; encrypt bodies
- `install.sh`: `--api-prefix` → `ApiPrefix` in config.json；`--api-key` unchanged

## Admin UI

- Add `server_api_prefix` (+ generate optional)
- Keep existing 通讯密钥 UI； desc: 同时用于节点 API SM4 派生
- No second key field

## One-click install

```text
wget -N https://raw.githubusercontent.com/Jireh012/v2node/main/script/install.sh && bash install.sh \
  --api-host <url> --node-id <id> --api-key <server_token> --api-prefix <prefix>
```

No `--sm4-key`.

## Trade-offs

| Choice | Why |
|--------|-----|
| Reuse 通讯密钥 | No extra secret; matches install UX |
| SHA-256 truncate | Tokens are arbitrary length (≥16), not valid raw SM4 keys |
| Rotate token = rotate SM4 | Accepted by product |
