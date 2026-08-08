# System Config (admin save / merge)

> Nested `v2_system_config` JSON merge rules for admin fetch/save.

---

## Scenario: Mutable nested maps for deepMerge

### 1. Scope / Trigger

- Trigger: `ConfigService.save` / `getFullConfig` deep-merge defaults with DB JSON.
- Symptom if broken: after first admin save of any site field, subsequent `GET /admin/config/fetch` returns 「获取配置失败」 (`UnsupportedOperationException`).

### 2. Signatures

```java
Map<String, Object> getFullConfig();
void save(Map<String, Object> body); // deepMerge then persist name=v2board
private void deepMerge(Map<String, Object> target, Map<String, Object> source);
private Map<String, Object> buildDefaults();
```

Table: `v2_system_config` row `name = v2board`, `value` = nested JSON (`site`, `safe`, `subscribe`, …).

### 3. Contracts

- Defaults and every nested section must be **mutable** (`HashMap` / `LinkedHashMap`).
- Never use `Map.of(...)` for sections that `deepMerge` or `putPhpSection` will write into.
- After `save` with body containing `site`, call `SubscribeRouteRegistrar.refresh()` (hot subscribe path).

### 4. Validation & Error Matrix

| Condition | Result |
|-----------|--------|
| Nested target is `Map.of` / unmodifiable | `UnsupportedOperationException` on put → admin fetch/save 500 |
| Nested target is HashMap | Merge OK |
| Save body only `{ "site": {…} }` | Deep-merge into full tree; entire tree serialized back to DB |

### 5. Good/Base/Bad Cases

- Good: Save `site.app_name`, then fetch returns full tree with new name.
- Base: Empty DB → defaults only; fetch succeeds.
- Bad: `data.put("frontend", Map.of(...))` in `buildDefaults`.

### 6. Tests Required

- Unit: `ConfigServiceSaveFetchTest` — save site override then `getFullConfig` / `getAppName` without throw.
- Assertion: no `UnsupportedOperationException` when merging stored JSON into defaults.

### 7. Wrong vs Correct

#### Wrong

```java
data.put("email", Map.of(
    "email_host", "",
    "email_password", ""
));
// later deepMerge(defaults, stored) → crash
```

#### Correct

```java
data.put("email", mutableMap(
    "email_host", "",
    "email_password", ""
));
// deepMerge also copies non-HashMap children before recurse
```

---

## Common Mistake: Admin save then fetch always fails

**Symptom**: System config page loads once (defaults), save succeeds, reload shows 「获取配置失败」.

**Cause**: Immutable nested maps in `buildDefaults()`; DB now has full JSON so merge always touches those sections.

**Fix**: Mutable section maps + defensive copy in `deepMerge`.

**Prevention**: Any new config section in `buildDefaults` must use `HashMap` / `mutableMap`, never `Map.of`.

---

## Scenario: Nested config consumers (email SMTP)

### 1. Scope / Trigger

- Trigger: Runtime readers of admin-saved groups (`email`, `safe`, `site`, …).
- Symptom if broken: Admin saves SMTP; send still uses empty / yml leftovers because code reads top-level keys.

### 2. Signatures

```java
String getStringFromGroup(String group, String key);
int getEmailVerify(); // safe.email_verify
int getSafeModeEnable(); // safe.safe_mode_enable
String getSecurePath(); // safe.secure_path → "admin" if empty/invalid; save validates ≥8 alnum + not reserved
void MailService.applyDynamicMailConfig(); // package-visible for tests
```

### 3. Contracts

- Full config shape is **nested**: `full.get("email")` → map with `email_host`, …
- Prefer dedicated getters / `getStringFromGroup` / `intFromGroup`; never `full.get("email_host")`.
- SMTP is **DB/admin only** — do not wire `MAIL_HOST` / `spring.mail.*` in yml or `.env.example`.
- `MailConfig` provides empty `JavaMailSenderImpl`; credentials applied at send time.

### 4. Validation & Error Matrix

| Condition | Result |
|-----------|--------|
| Top-level key read | Always empty → send fails or uses stale sender state |
| `email_encryption` empty | Must clear `mail.smtp.ssl.enable` / starttls (override prior state) |
| Admin test mail | Use `sendEmailSync` so API surfaces SMTP errors |

### 5. Good/Base/Bad Cases

- Good: Save `email.email_host` → `getStringFromGroup("email","email_host")` returns host.
- Base: Empty email section → empty strings; send fails loudly.
- Bad: Reintroduce `spring.mail.host: ${MAIL_HOST:}` as second source of truth.

### 6. Tests Required

- `ConfigServiceEmailNestedTest`, `MailServiceConfigTest` (see [passport-email.md](./passport-email.md)).

### 7. Wrong vs Correct

#### Wrong

```yaml
spring:
  mail:
    host: ${MAIL_HOST:}
```

#### Correct

```java
@Configuration
public class MailConfig {
  @Bean JavaMailSender javaMailSender() { return new JavaMailSenderImpl(); }
}
// credentials from email.* via MailService
```

---

## Convention: Site / invite / ticket defaults in yml are bootstrap only

**What**: `application.yml` `v2board.app-name`, `subscribe-path`, invite/ticket numbers are **defaults** when DB empty — not env-driven overrides for operator day-to-day settings.

**Why**: Operators change these in admin「系统配置」; dual env+DB sources caused drift (subscribe path, SMTP, app name).

**Related**: Keep `APP_KEY`, `DB_*`, `REDIS_*`, external-subscribe probe envs.
