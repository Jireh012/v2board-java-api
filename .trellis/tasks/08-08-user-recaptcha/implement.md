# Implement: user login/register reCAPTCHA v2

## Checklist

1. [x] API: `ConfigService` getters for enable / site_key / secret
2. [x] API: `RecaptchaService` + unit test (mock HTTP / injectable client)
3. [x] API: `CommController` public plaintext + `CommControllerConfigTest`
4. [x] API: `AuthController#login` optional `recaptcha_data` + verify when enabled
5. [x] API: `PassportService#register` verify when enabled
6. [x] Spec: `public-site-config.md` / `passport-email.md`
7. [x] UI: `site.ts` / `siteBrand` flags + site_key
8. [x] UI: `RecaptchaV2` widget; `LoginView` + `RegisterView`
9. [x] UI: `auth.ts` pass `recaptcha_data`
10. [ ] Manual: enable with test keys / real keys; login+register pass; fake token fail

## Validation

```bash
mvn -Dtest=CommControllerConfigTest,RecaptchaServiceTest test
# UI: toggle enable, confirm widget, reject without token
```

## Risky files

- `AuthController.java` login signature (form param)
- Login/Register layout + script loading race
- Public config must not leak secret

## Rollback points

- After API-only: leave UI off → enable still blocks API if toggled (warn ops)
- Prefer ship API+UI together; emergency: set `recaptcha_enable=0`
