# 面板 API 动作名去特征

## Goal

Remove V2Board-style action fingerprints (`getSubscribe`, `trafficLog`, `info`, `fetch`, …) from external URLs under passport/user/admin prefixes. PHP alignment remains **functional** only; outer paths are opaque aliases derived from `SM4_KEY`.

## Requirements

1. External panel URLs are `{prefix}/{12-hex-alias}` only (single opaque segment). No classic action names on the wire.
2. Alias = first 12 hex chars of `SHA-256(UTF-8(SM4_KEY + "\0" + zone + "\0" + classicRel))` where `classicRel` has no leading slash (e.g. `getSubscribe`, `order/fetch`, `server/vmess/save`).
3. Controllers keep internal `@RequestMapping("/api/v1/...")`. Filter rewrites alias → internal classic path.
4. Hard cutover:
   - `/api/v1/{passport|user|admin}/**` → 404 (existing)
   - `{prefix}/getSubscribe` (or any non-alias remainder) → 404
5. Plaintext allowlist unchanged: payment notify, Telegram webhook, subscribe path.
6. Frontend `apiUrl(zone, classicPath)` always emits aliased URLs (`VITE_SM4_KEY` must match `SM4_KEY`).
7. Trellis code-specs updated (backend + frontend `panel-api-sm4.md`).

## Acceptance Criteria

- [x] Network tab shows no `getSubscribe` / `trafficLog` / `info` / `fetch` path segments under panel prefixes (`apiUrl` emits aliases)
- [x] `GET /api/v1/user/getSubscribe` → 404
- [x] `GET {userPrefix}/getSubscribe` → 404
- [x] `GET {userPrefix}/{alias(getSubscribe)}` reaches handler (401 without auth; Panel SM4 zone)
- [x] Unit tests for alias derive/resolve + filter rewrite
- [x] Specs document algorithm, catalog maintenance, Wrong/Correct

## Notes

- Parent: `08-09-panel-api-sm4-obfuscate`
- Query param names out of scope
