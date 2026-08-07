# Backend Development Guidelines

> Best practices for backend development in this project.

---

## Overview

Code-specs for the Spring Boot / MyBatis-Plus API that shares MySQL + Redis with PHP V2Board.

---

## Guidelines Index

| Guide | Description | Status |
|-------|-------------|--------|
| [Directory Structure](./directory-structure.md) | Module organization and file layout | To fill |
| [Database Guidelines](./database-guidelines.md) | ORM, `v2_*` schema, PHP JSON field types | Active |
| [External Subscribe](./external-subscribe.md) | Third-party subscribe sync status lifecycle | Active |
| [Subscribe Delivery](./subscribe-delivery.md) | `subscribe_url` resolution + panel/external name markers | Active |
| [Error Handling](./error-handling.md) | `BusinessException`, silent-catch bans | Active |
| [Quality Guidelines](./quality-guidelines.md) | Code standards, forbidden patterns | To fill |
| [Logging Guidelines](./logging-guidelines.md) | Structured logging, log levels | To fill |

---

## Pre-Development Checklist

When changing admin servers, JSON columns, or external subscribe:

- [ ] Read [database-guidelines.md](./database-guidelines.md) if touching PHP-written JSON (`group_id`, etc.)
- [ ] Read [external-subscribe.md](./external-subscribe.md) if changing sync status, locks, or admin sync APIs
- [ ] Read [subscribe-delivery.md](./subscribe-delivery.md) if changing `getSubscribe`, subscribe path/URL, or client node naming
- [ ] Read [error-handling.md](./error-handling.md) — do not swallow conversion errors in list aggregators
- [ ] Confirm durable job states have startup recovery if paired with in-memory locks
- [ ] Prefer `ConfigService.buildSubscribeUrl` over raw `@Value` for subscribe links

---

## How to Fill These Guidelines

1. Document the project's **actual** conventions (not ideals)
2. Include code examples from this codebase
3. List forbidden patterns and why
4. Prefer the 7-section Scenario template for infra / cross-layer contracts

**Language**: All documentation in this directory should be written in **English**.
