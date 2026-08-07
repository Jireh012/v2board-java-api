# Database Guidelines

> Database patterns and conventions for this project (MySQL `v2_*` + MyBatis-Plus).

---

## Overview

- Shared schema with PHP V2Board: tables prefixed `v2_`, no Flyway/Liquibase in Java.
- Java-only DDL lives under `src/main/resources/db/*.sql` and is applied manually.
- Entity fields are camelCase; columns are snake_case (`map-underscore-to-camel-case: true`).
- JSON columns use `@TableField(typeHandler = JacksonTypeHandler.class)` and `autoResultMap = true` on `@TableName`.

---

## Naming Conventions

| Kind | Convention | Example |
|------|------------|---------|
| Table | `v2_<entity>` | `v2_server_v2node` |
| Column | snake_case | `group_id`, `last_sync_status` |
| Entity | PascalCase | `ServerV2node` |
| Timestamps | unix seconds (`bigint`) unless legacy PHP column differs | `created_at`, `last_sync_at` |

---

## Scenario: PHP-compatible JSON arrays (`group_id`)

### 1. Scope / Trigger

- Trigger: PHP may store JSON number arrays as **strings** (`["1","2"]`) or numbers (`[1,2]`).
- Mapping `group_id` → `List<Integer>` + `ObjectMapper.convertValue` / JacksonTypeHandler throws `ClassCastException`.
- If admin list swallows the exception per row, **entire protocol types (e.g. all v2nodes) disappear** from 节点管理.

### 2. Signatures

```java
// ServerV2node / similar server entities
@TableField(value = "group_id", typeHandler = JacksonTypeHandler.class)
private List<Object> groupId;   // NOT List<Integer>
```

Same pattern already used by `ServerVless.groupId`.

### 3. Contracts

| Layer | Contract |
|-------|----------|
| MySQL | JSON / text JSON array of group ids (string or number elements) |
| Entity | `List<Object>` (elements `Integer`, `Long`, or `String`) |
| Admin API map | After `convertValue`, callers must coerce ids with `String.valueOf` / parseInt when needed |
| Consumers | Never assume every element is `Integer` |

### 4. Validation & Error Matrix

| Condition | Result |
|-----------|--------|
| `group_id` JSON `["1"]` with `List<Integer>` field | Deserialize / convert failure |
| Failure swallowed in `addServersWithType` | Node omitted from list (silent data loss) |
| Correct `List<Object>` | Row included; ids remain loosely typed |

### 5. Good / Base / Bad Cases

- **Good**: `group_id = ["1","2"]` loads into `List<Object>` and appears in admin list.
- **Base**: `group_id = [1,2]` also loads.
- **Bad**: `List<Integer> groupId` + empty `catch (Exception ignored)` around `convertValue`.

### 6. Tests Required

- Unit/fixture: entity with `group_id` JSON strings round-trips through JacksonTypeHandler.
- Integration: `AdminManageController` getNodes returns a v2node row whose DB `group_id` is `["1"]`.
- Assert conversion failures are **logged**, not silently dropped.

### 7. Wrong vs Correct

#### Wrong

```java
private List<Integer> groupId;

try {
    Map<String, Object> map = objectMapper.convertValue(s, ...);
    target.add(map);
} catch (Exception ignored) {
    // v2node vanished from UI
}
```

#### Correct

```java
private List<Object> groupId;

try {
    Map<String, Object> map = objectMapper.convertValue(s, ...);
    target.add(map);
} catch (Exception e) {
    logger.error("Failed to convert server type={}: {}", type, e.toString());
}
```

---

## Query Patterns

- Prefer MyBatis-Plus `LambdaQueryWrapper` / `BaseMapper` methods; avoid custom XML unless necessary.
- Soft filters for admin/user lists should match PHP semantics (`enable`, `show`, group membership).

---

## Migrations

- No automated migration runner.
- New Java-only tables: add `src/main/resources/db/<name>.sql` and document in `ENV_CONFIG.md` / README.
- Do not invent columns that break PHP writers without a dual-write plan.

---

## Common Mistakes

### Common Mistake: Strict Java types for PHP JSON

**Symptom**: Admin node list missing a whole protocol (e.g. no v2node).

**Cause**: `group_id` (or similar) stored as JSON strings; `List<Integer>` + silent catch.

**Fix**: Use `List<Object>` (or custom deserializer); log conversion failures.

**Prevention**: When adding entities that mirror PHP JSON columns, copy the loosest existing type pattern (`ServerVless`).
