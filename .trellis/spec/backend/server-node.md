# Server Node Config (UniProxy / v2node)

> Panel↔node communication token, intervals, and `base_config` contract.

---

## Scenario: UniProxy / V2Server `base_config`

### 1. Scope / Trigger

- Trigger: Node pulls `/api/v1/server/UniProxy/config` or `/api/v2/server/config`.
- Symptom if broken: v2node ignores low-traffic filters; push/pull timing drifts from admin「节点」settings.

### 2. Signatures

```java
// UniProxyController / V2ServerController config response
resp.put("base_config", Map); // mutable LinkedHashMap preferred
```

Fields (ints from `server.*` section):

| Response key | Config key | Default |
|--------------|------------|---------|
| `push_interval` | `server_push_interval` | 60 |
| `pull_interval` | `server_pull_interval` | 60 |
| `node_report_min_traffic` | `server_node_report_min_traffic` | 0 |
| `device_online_min_traffic` | `server_device_online_min_traffic` | 0 |

### 3. Contracts

- All four keys must be present in `base_config` (aligned with V2Server / v2node).
- Traffic thresholds are enforced on the **node** side; panel does not re-filter push payload.
- `device_limit_mode` (0 = per-node alive count, 1 = global IP dedupe) remains in UniProxy `/alive` only — not in `base_config`.

### 4. Validation & Error Matrix

| Condition | Result |
|-----------|--------|
| Configured `server_token` blank or length &lt; 16 | Auth fail `"token is error"` (empty client token must not match empty config) |
| Client `token` missing | `"token is null"` |
| Client token ≠ configured (≥16) | `"token is error"` |

### 5. Tests Required

- `UniProxyControllerTest` — `buildBaseConfig` four fields; token validity helper.
- `V2ServerControllerTest` — `base_config` + token helper.

---

## Scenario: Admin save `server` group

See [system-config.md](./system-config.md) — Scenario: Server node group validation.
