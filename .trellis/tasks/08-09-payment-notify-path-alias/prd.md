# 支付回调路径去特征

## Goal

Replace classic plaintext fingerprint `/api/v1/guest/payment/notify/{method}/{uuid}` with a configurable prefix. Body stays plaintext (gateway requirement).

## Requirements

1. `site.payment_notify_prefix` — empty → auto-gen `/g/`+12 alnum; hot-refresh via `ClientApiPathRegistry`
2. External: `{prefix}/{method}/{uuid}` → internal `/api/v1/guest/payment/notify/{method}/{uuid}`
3. Classic `/api/v1/guest/payment/**` → 404
4. No Panel SM4 / `X-A` on notify path
5. `PaymentService.buildNotifyUrl` + admin `notify_url` use new path
6. Spec + admin UI field

## Acceptance Criteria

- [x] Classic notify URL → 404
- [x] `{prefix}/{method}/{uuid}` reaches controller (plaintext `gate is not enable`)
- [x] `buildNotifyUrl` / admin fetch use `buildPaymentNotifyPath`
