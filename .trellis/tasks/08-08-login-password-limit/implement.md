# Implement: login password error limit

## Checklist

1. [x] ConfigService getters for password_limit_*
2. [x] Enforce via `LoginPasswordLimitService` in `AuthController#login`
3. [x] Unit test locked / increment / disabled
4. [x] UI: fix AdminSystemConfigView copy (邮箱 not IP)
5. [x] Spec note in passport-email.md

## Validation

```bash
mvn -Dtest=LoginPasswordLimitServiceTest test
# Manual: enable, fail password N times, confirm lock message + window
```
