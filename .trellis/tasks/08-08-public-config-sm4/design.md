# Design: 公开配置 SM4-CBC

## Boundaries

| Layer | Change |
|-------|--------|
| API | `Sm4Properties` / `Sm4Util`；`CommController#config` 加密信封 |
| Config | `v2board.sm4-key` ← `SM4_KEY`；`.env.example` |
| UI | `sm-crypto`；`api/site.ts` decrypt；`VITE_SM4_KEY` |
| Spec | `public-site-config.md` envelope contract |

## Wire format

Plain JSON (UTF-8) before encrypt — same fields as today:

```json
{
  "app_name": "...",
  "stop_register": 0,
  "invite_force": 0,
  "email_verify": 0,
  "safe_mode_enable": 0
}
```

Response:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "iv": "<base64 16-byte IV>",
    "payload": "<base64 ciphertext>"
  }
}
```

## Crypto details

- Algorithm: `SM4/CBC/PKCS7Padding` via BouncyCastle
- Key: 16 bytes — if `SM4_KEY` length is 32 and hex-charset, decode hex; else UTF-8 bytes must be length 16
- IV: `SecureRandom` 16 bytes per request
- No AAD / no MAC in MVP (CBC only)

## Key loading

```yaml
v2board:
  sm4-key: ${SM4_KEY:}
```

If blank at encrypt time → `BusinessException(500, "SM4 key not configured")` (no plaintext fallback).

UI: `import.meta.env.VITE_SM4_KEY`; missing → decrypt skip + existing brand fallbacks.

## Compatibility

**Breaking** for any client that expected plaintext `data.app_name`. In-repo UI updated in same change set. No PHP compatibility required for this Java UI path.

## Rollback

Revert controller encrypt + UI decrypt; or temporarily return plaintext behind flag (not planned — keep fail-closed).
