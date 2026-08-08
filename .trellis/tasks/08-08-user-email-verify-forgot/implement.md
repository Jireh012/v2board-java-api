# Implement: 用户端邮箱验证与找回密码

## Checklist

1. [x] API: `ConfigService.getEmailVerify()`；`CommController` 返回 `email_verify`
2. [x] API: update `CommControllerConfigTest`（含新字段、仍无敏感 key）
3. [x] Spec: update `.trellis/spec/backend/public-site-config.md`
4. [x] UI: `PublicSiteConfig` + `siteBrand.emailVerify`
5. [x] UI: `auth.ts` — `sendEmailVerify` / `register(+email_code)` / `forget`
6. [x] UI: `RegisterView` 条件验证码 + 冷却
7. [x] UI: `ForgetView` + `router` `/forget`
8. [x] UI: `LoginView` 忘记密码链接
9. [x] Spec (UI): extend `site-brand.md`（或同等）
10. [ ] Manual: toggle email_verify；发码/注册/找回（需可用 SMTP）

## Validation

```bash
# api
mvn -Dtest=CommControllerConfigTest test

# ui typecheck if available
npm run build
```

## Risky files

- `RegisterView.vue` — 勿破坏 `stop_register` / `invite_force` 现有门禁
- `CommController` — 禁止泄露完整 config
- `sendEmailVerify` — 勿改限流语义

## Rollback points

After step 2 (API only) UI still works. After UI, disable via `email_verify=0` + hide forget link if needed (not planned).
