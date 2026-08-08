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
