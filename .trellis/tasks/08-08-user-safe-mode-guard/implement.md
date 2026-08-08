# Implement: 用户端安全模式访问守卫

## Checklist

1. [x] API: `getSafeModeEnable()` + `CommController` 字段
2. [x] API: `CommControllerConfigTest` 更新 size/keys
3. [x] Spec: `public-site-config.md`
4. [x] UI: `PublicSiteConfig` + `siteBrand.safeMode`
5. [x] UI: `router.beforeEach` 守卫 + login redirect 消费（可选但推荐）
6. [x] Spec UI: `site-brand.md`
7. [ ] Manual: 开关开/关，未登录访问 `/dashboard`

## Validation

```bash
mvn -Dtest=CommControllerConfigTest test
# UI: toggle safe mode, open /dashboard in private window
```

## Risky files

- `router.ts` — 勿破坏 admin 守卫与 `/forget` 公开访问
- `CommController` — 仅增非敏感字段；与 email_verify 并存

## Note

若 `08-08-user-email-verify-forgot` 尚未合并，本任务改同一文件时保留其 `email_verify` 字段。
