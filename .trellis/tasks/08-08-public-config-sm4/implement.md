# Implement: 公开配置 SM4-CBC

## Checklist

1. [x] API: `Sm4Util` + unit test round-trip
2. [x] API: `@Value` / properties for `SM4_KEY`; wire into `CommController`
3. [x] API: encrypt public config map → `{ iv, payload }`
4. [x] `.env.example` + `application.yml` + `ENV_CONFIG.md`
5. [x] Spec: update `public-site-config.md` envelope
6. [x] UI: add `sm-crypto`; `decryptPublicConfig`; update `site.ts`
7. [x] UI: `.env.example` / vite env `VITE_SM4_KEY`
8. [ ] Manual: curl sees ciphertext; UI brand still loads

## Validation

```bash
mvn -Dtest=Sm4UtilTest,CommControllerConfigTest test
# UI: set VITE_SM4_KEY matching SM4_KEY; load login page brand
```

## Risky files

- `CommController` — do not leak plaintext fields alongside envelope
- Key length validation — reject wrong sizes early

## Dev keys

Use a fixed 16-char dev key in local `.env` examples only (e.g. `0123456789abcdef`), never production reuse of documented sample without rotation note.
