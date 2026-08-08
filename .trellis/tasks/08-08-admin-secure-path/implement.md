# Implement: admin secure_path

## Checklist

1. [x] API: `getSecurePath()` + validate in `save` for `safe.secure_path`
2. [x] API: add to SM4 public plaintext + `CommControllerConfigTest`
3. [x] Spec: `public-site-config.md`
4. [x] UI: `siteBrand` `adminBasePath` / `adminUrl`
5. [x] UI: `main.ts` await brand before router; dynamic `router.ts`
6. [x] UI: `App.vue`, `AdminLayout.vue`, hard-coded pushes (`AdminUsersView`, etc.)
7. [x] UI: LoginView redirect deny list uses dynamic admin prefix
8. [x] Spec UI: site-brand admin path
9. [ ] Manual: set `admin888`, open `/admin888/login`, confirm `/admin` not admin

## Validation

```bash
mvn -Dtest=CommControllerConfigTest,ConfigServiceSecurePathTest test
# UI: save path, hard refresh, navigate
rg -n "['\"]/admin" src --glob '!**/api/**'  # should be minimal / gone for UI routes
```

## Risky files

- `AdminLayout.vue` nav arrays
- `router.ts` beforeEach
- Any `router.push('/admin...')` in admin views
