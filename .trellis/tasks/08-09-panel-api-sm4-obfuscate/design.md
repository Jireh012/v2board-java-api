# Design — Panel API SM4 (parent)

## Cross-cutting

| Surface | Path strategy | SM4 key | Auth on wire |
|---------|---------------|---------|--------------|
| Node | `{server_api_prefix}/{c,u,p,a,l}` | `SHA256(server_token)[:16]` | query `e` |
| User + Passport | `{user_api_prefix|passport_prefix}/{actionAlias}` | `SM4_KEY` | envelope + `X-A`; aliases: child `08-09-panel-api-action-alias` |
| Admin | `{admin_api_prefix}/{actionAlias}` | `SM4_KEY` | same as user |
| Allowlist | classic paths unchanged | none | N/A |

## Plaintext allowlist (do not wrap)

- `PaymentCallbackController` `/api/v1/guest/payment/notify/**`
- `GuestTelegramController` `/api/v1/guest/telegram/**`
- Subscribe dynamic route (`SubscribeRouteRegistrar`)
- Keep these **outside** encrypted prefix registrars

## Shared building blocks (implement in first child that needs them, reuse)

- `Sm4Util` envelope (exists)
- Panel request/response advice for “encrypted API” zone
- Config keys for prefixes + validation/auto-gen
- Frontend HTTP interceptor (user + admin apps)

## Suggested order

1. Node child (already detailed)
2. Shared panel SM4 filter + user/passport child
3. Admin child (reuse filter)

## Parent does not ship product code alone

Integration review: PAC1–PAC4 after children complete.
