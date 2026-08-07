# Error Handling

> How errors are handled in this project.

---

## Overview

- Business failures throw `BusinessException(code, message)`.
- `GlobalExceptionHandler` maps them to `ApiResponse` with SNAKE_CASE JSON (`code`, `message`, `data`).
- Success responses use `code = 0`.

---

## Error Types

| Type | When | Client |
|------|------|--------|
| `BusinessException` | Expected domain failure (not found, conflict, validation) | `{ code, message }` from exception |
| Unchecked / other | Unexpected | Handled by global handler (log + generic/error body) |

```java
throw new BusinessException(500, "订阅源不存在");
throw new BusinessException(500, "同步任务正在进行中，请稍后再试");
```

---

## Error Handling Patterns

- Prefer fail-fast with `BusinessException` at controller/service boundaries.
- Long-running jobs that persist status (e.g. external subscribe sync) must write terminal `failed`/`success` even when the JVM dies later — see [external-subscribe.md](./external-subscribe.md).
- Never use empty `catch` around entity→map conversion in list aggregators; log and continue so operators can see data loss.

---

## API Error Responses

```json
{ "code": 500, "message": "同步任务正在进行中，请稍后再试", "data": null }
```

---

## Common Mistakes

### Common Mistake: Silent catch drops whole entity classes

**Symptom**: Admin list missing all rows of one server type.

**Cause**: `objectMapper.convertValue` fails (JSON type mismatch); empty catch skips the row.

**Fix**: Log the exception with server type/id; fix entity JSON field types (`List<Object>` for PHP arrays).

**Prevention**: See [database-guidelines.md](./database-guidelines.md) — Scenario: PHP-compatible JSON arrays.

### Common Mistake: Durable `running` without crash recovery

**Symptom**: UI shows RUNNING / 同步中 forever after restart.

**Cause**: Status written to DB; in-memory lock cleared on process exit.

**Fix / Prevention**: [external-subscribe.md](./external-subscribe.md) recovery on `ApplicationReadyEvent` and after acquiring sync lock.
