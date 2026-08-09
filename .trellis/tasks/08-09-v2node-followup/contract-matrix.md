# Contract Matrix — v2node ↔ Java ↔ PHP

Sources:

- v2node: `api/v2board/panel.go`, `node.go`, `user.go`
- Java: `V2ServerController`, `UniProxyController`
- PHP: [wyx2685/v2board](https://github.com/wyx2685/v2board) `V1/Server/UniProxyController`, `V2/Server/ServerController`

Status legend: `ok` | `drift` | `fix-java` | `fix-v2node` | `defer`

Common query (v2node `panel.New`): `token`, `node_id`, `node_type=v2node` on every request.

---

## 1. `GET /api/v2/server/config`

| Aspect | v2node expects | PHP | Java | Status |
|--------|----------------|-----|------|--------|
| Auth | query `token` | empty → fail msg; mismatch → fail msg (HTTP 200 body) | same; plus configured token length ≥16 | `ok` (Java stricter ≥16 intentional) |
| Query | `token`, `node_id` (+ `node_type` unused) | `token`, `node_id`; loads `v2node` server | same | `ok` |
| Request headers | `If-None-Match` | ETag compare → 304 | SHA1 body ETag → 304 | `ok` (not byte-identical to PHP) |
| Response: protocol fields | `CommonNode`: protocol, listen_ip, server_port, network*, tls*, cipher, server_key, hysteria/tuic/anytls fields, routes | same set + ignore_client_bandwidth | `buildV2nodeConfig` same set | `ok` |
| Response: `base_config` | push/pull + `node_report_min_traffic` + `device_online_min_traffic` | all four | all four | `ok` |
| Fail shape | client treats non-200 / decode err | `{status:fail,message}` HTTP 200 | same | `ok` |

---

## 2. `GET /api/v1/server/UniProxy/user`

| Aspect | v2node expects | PHP | Java | Status |
|--------|----------------|-----|------|--------|
| Auth | token + node via query | constructor: token + node_type + node_id + server exists | `resolveNodeContext` same | `ok` |
| Request headers | `If-None-Match`, `X-Response-Format: msgpack` | msgpack / JSON + ETag 304 | same | `ok` |
| Response envelope | `{users:[...]}` | same | same | `ok` |
| UserInfo `id` | required | from user model | put if non-null | `ok` |
| UserInfo `uuid` | required | from user model | put if non-null | `ok` |
| UserInfo `speed_limit` | required (int; 0 = unlimited) | included via `user->toArray()` when non-null | **was missing** → add `speed_limit` | `fix-java` → fixed |
| UserInfo `device_limit` | required | from user model | put if non-null | `ok` |
| Extra user fields | ignored by v2node | all non-null columns | curated subset (group_id, traffic, expired_at, …) | `ok` (superset/subset both fine for v2node) |
| Content-Type msgpack | `application/x-msgpack` | same | same | `ok` |

---

## 3. `POST /api/v1/server/UniProxy/push`

| Aspect | v2node expects | PHP | Java | Status |
|--------|----------------|-----|------|--------|
| Auth | token + node | constructor | `resolveNodeContext` | `ok` |
| Body | JSON `map[uid][up,down]` int64 | JSON map | `Map<String, List<Long>>` | `ok` |
| Min-traffic filter | node-side (`node_report_min_traffic`) | panel accepts as-is | panel accepts as-is | `ok` |
| Response | truthy / no strict parse | `{data:true}` | `{data:true}` | `ok` |
| Empty body | — | 400 / empty handling | BusinessException 400 `Invalid traffic data` | `ok` |

---

## 4. `POST /api/v1/server/UniProxy/alive`

| Aspect | v2node expects | PHP | Java | Status |
|--------|----------------|-----|------|--------|
| Auth | token + node | constructor | `resolveNodeContext` | `ok` |
| Body | JSON `map[uid][]IP` | same | `Map<String, List<String>>` | `ok` |
| Device count mode | node sends IPs; panel counts | `device_limit_mode` 0/1 | same | `ok` |
| Min online traffic | node-side (`device_online_min_traffic`) | N/A on panel | N/A on panel | `ok` |
| Response | — | `{data:true}` | `{data:true}` | `ok` |

---

## 5. `GET /api/v1/server/UniProxy/alivelist`

| Aspect | v2node expects | PHP | Java | Status |
|--------|----------------|-----|------|--------|
| Auth | sends token/node query always | **constructor requires token + valid node** | **was unauthenticated** → call `resolveNodeContext` | `fix-java` → fixed |
| Response | `{alive: map[uid]count}` | `{alive: object}` | `{alive: map}` | `ok` |
| Cache | — | `ALIVE_LIST` 60s | same key/TTL | `ok` |
| Client error handling | HTTP ≥399 → empty map (no hard fail) | `abort(500, …)` | `BusinessException(500,…)` → GlobalExceptionHandler HTTP **400** + body; v2node soft-fails | `ok` (compatible; not PHP status-identical) |

---

## Summary of actions

| Item | Status | Action |
|------|--------|--------|
| V2 `base_config` four fields | `ok` | none |
| Token ≥16 helpers / fail paths | `ok` | covered by tests |
| user `speed_limit` | `fix-java` | `UniProxyController` user payload |
| alivelist auth | `fix-java` | `resolveNodeContext` before body |
| ETag algorithm byte-identity vs PHP | `ok` / `defer` live smoke | not required; 304 behavior aligned |
| Real v2node process E2E | `defer` | out of scope (AC4) |

## Defer

- Live v2node process smoke against a running panel (PRD验证深度 1).
- ETag / msgpack byte-level identity with PHP responses.
