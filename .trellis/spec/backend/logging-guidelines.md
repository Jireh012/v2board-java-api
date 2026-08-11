# Logging Guidelines

> How logging is done in this project.

---

## Overview

- Library: SLF4J + Logback (Spring Boot default).
- File / console logging stays on Boot defaults.
- **ERROR and above** are also persisted to PHP-compatible MySQL table `v2_log` for admin ops UI.

---

## ERROR → `v2_log`

### Pipeline

```
logger.error(...) / uncaught Exception
  → Logback root + DbErrorAppender (ThresholdFilter ERROR)
  → SystemLogService.buildRow + persistAsync(systemLogExecutor)
  → INSERT v2_log
```

- Registrar: `DbErrorAppenderRegistrar` on `ApplicationReadyEvent`.
- `GlobalExceptionHandler` only calls `log.error` for unexpected exceptions — do **not** insert again in the handler.
- Ordinary `BusinessException` / validation handlers do **not** call `log.error` → not stored.

### Non-HTTP context

| Field | Value |
|-------|--------|
| `uri` | `-` |
| `method` | `SCHEDULE` |
| `ip` | empty |
| `host` | local hostname |

### Safety

| Rule | Detail |
|------|--------|
| Fallback logger | `system-log-fallback` — write failures only; **no** Db appender |
| Re-entry | `ThreadLocal` while inserting |
| Truncation | title ~2k, context ~8k, data ~4k |
| Redaction | password / token / authorization / secret / sm4 / app_key (query + JSON keys) |
| Async pool | `systemLogExecutor` — queue 500, `DiscardOldestPolicy` under ERROR storm |
| Retention | `LogSchedule` deletes rows with `created_at` older than 30 days |

### Admin API

- `GET /api/v1/admin/system/getSystemLog` — `current`, `pageSize`/`page_size`, optional `level`
- Catalog: `system/getSystemLog`
- DDL: `src/main/resources/db/v2_log.sql` (`CREATE TABLE IF NOT EXISTS`)

---

## Log Levels

| Level | Use |
|-------|-----|
| ERROR | Unexpected failures; persisted to `v2_log` |
| WARN | Degraded / retryable conditions (file only) |
| INFO | Lifecycle / ops milestones (file only) |
| DEBUG | Diagnostics (file only; not enabled in prod by default) |

---

## What NOT to Log (plaintext in `v2_log`)

- Passwords, JWT / Bearer tokens, `Authorization`, Panel `SM4_KEY`, `APP_KEY`, API keys
- Prefer redacted placeholders (`***`) in `data` / `context`
