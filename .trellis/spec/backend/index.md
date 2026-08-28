# Backend Development Guidelines

> Best practices for backend development in this project.

---

## Overview

Code-specs for the Spring Boot / MyBatis-Plus API that shares MySQL + Redis with PHP V2Board.

**PHP upstream (canonical)**: [wyx2685/v2board](https://github.com/wyx2685/v2board) `master` — see [php-upstream.md](./php-upstream.md).

**v2node upstream (compare/merge)**: [wyx2685/v2node](https://github.com/wyx2685/v2node) `main` — see [v2node-upstream.md](./v2node-upstream.md). Deploy/install remains [Jireh012/v2node](https://github.com/Jireh012/v2node).

---

## Guidelines Index

| Guide | Description | Status |
|-------|-------------|--------|
| [PHP Upstream](./php-upstream.md) | Canonical PHP repo for align/compare work | Active |
| [v2node Upstream](./v2node-upstream.md) | Pointer: compare [wyx2685/v2node](https://github.com/wyx2685/v2node); full specs in sibling v2node Trellis | Active |
| [Admin Stat](./admin-stat.md) | Dashboard `/admin/stat/*` KPIs, ranks, `getStatServer` node traffic query | Active |
| [Directory Structure](./directory-structure.md) | Module organization and file layout | To fill |
| [Database Guidelines](./database-guidelines.md) | ORM, `v2_*` schema, PHP JSON field types | Active |
| [Admin Commerce](./admin-commerce.md) | Orders / coupons / giftcards / plan units & filters | Active |
| [Invite Commission](./invite-commission.md) | Nested `invite.*` readers; withdraw limit in cents | Active |
| [Admin User](./admin-user.md) | User remarks/staff, traffic bytes, ticket reply form | Active |
| [Admin Content](./admin-content.md) | Notice / knowledge admin CRUD, sort wire `knowledge_ids` | Active |
| [External Subscribe](./external-subscribe.md) | Third-party subscribe sync status lifecycle | Active |
| [Subscribe Delivery](./subscribe-delivery.md) | `subscribe_url` resolution + panel/external name markers | Active |
| [Subscribe Rules](./subscribe-rules.md) | ACL4SSR templates: Redis/DB resolve, sanitize, builders | Active |
| [Public Site Config](./public-site-config.md) | Fixed `GET /config` SM4 envelope + public flags / API prefixes | Active |
| [Panel API SM4](./panel-api-sm4.md) | Prefixes, action aliases, Panel SM4/`X-A`, payment notify `/g/` plaintext | Active |
| [Telegram Bot](./telegram.md) | Guest webhook, bind/traffic commands, admin notify | Active |
| [Passport Email](./passport-email.md) | sendEmailVerify / register code / forget; nested SMTP | Active |
| [System Config](./system-config.md) | Admin save/fetch deepMerge; nested readers; no MAIL_* env | Active |
| [Server Node](./server-node.md) | Obfuscated node API/SM4, `base_config`, install_command + Jireh012 Release | Active |
| [Job Queue](./job-queue.md) | Redis persistent queues + admin workload/failed (non-Horizon) | Active |
| [Error Handling](./error-handling.md) | `BusinessException`, silent-catch bans | Active |
| [Quality Guidelines](./quality-guidelines.md) | Code standards, forbidden patterns | To fill |
| [Logging Guidelines](./logging-guidelines.md) | Structured logging, log levels | To fill |

---

## Pre-Development Checklist

When changing admin servers, JSON columns, or external subscribe:

- [ ] Aligning with PHP / “对照原版” → [php-upstream.md](./php-upstream.md) ([wyx2685/v2board](https://github.com/wyx2685/v2board))
- [ ] Aligning / merging v2node / “对照更新节点” → [v2node-upstream.md](./v2node-upstream.md) ([wyx2685/v2node](https://github.com/wyx2685/v2node) `main`); install still [Jireh012/v2node](https://github.com/Jireh012/v2node)
- [ ] Admin dashboard / `AdminStatController` / node traffic query → [admin-stat.md](./admin-stat.md)
- [ ] Read [database-guidelines.md](./database-guidelines.md) if touching PHP-written JSON (`group_id`, etc.)
- [ ] Read [admin-commerce.md](./admin-commerce.md) if touching orders, coupons, giftcards, plan prices/traffic
- [ ] Read [invite-commission.md](./invite-commission.md) if changing invite/commission/withdraw runtime readers
- [ ] Read [admin-user.md](./admin-user.md) if touching admin user fields (`remarks`, traffic bytes) or ticket reply
- [ ] Read [admin-content.md](./admin-content.md) if touching admin notice/knowledge APIs or sort payload
- [ ] Read [external-subscribe.md](./external-subscribe.md) if changing sync status, locks, or admin sync APIs
- [ ] Read [subscribe-delivery.md](./subscribe-delivery.md) if changing `getSubscribe`, subscribe path/URL, client node naming, or `ClientController.subscribe` **method signature** (dynamic route reflection)
- [ ] Read [subscribe-rules.md](./subscribe-rules.md) if changing rule templates, `?rule=` / `subscribe.rule_profile`, sanitize pipeline, bake scripts, or builders' template resolve
- [ ] Read [public-site-config.md](./public-site-config.md) if changing fixed `/config`, public flags, or bootstrap envelope fields
- [ ] Read [panel-api-sm4.md](./panel-api-sm4.md) if changing client API prefixes, action aliases, payment notify prefix, `PanelSm4Filter`, or `X-A`
- [ ] New panel endpoint → add classicRel to `PanelApiActionCatalog` (wire is `{prefix}/{alias}` only)
- [ ] Payment notify: `buildPaymentNotifyPath` only; classic `/api/v1/guest/payment/**` stays 404; never Panel SM4
- [ ] Read [telegram.md](./telegram.md) if changing webhook, bot commands, or Telegram admin notify
- [ ] Public/panel SM4: never plaintext fallback when `SM4_KEY` missing; keep secrets off `/config`; do not encrypt payment/telegram/subscribe
- [ ] Panel `SM4_KEY` ≠ node key from `server_token` — see [panel-api-sm4.md](./panel-api-sm4.md) + [server-node.md](./server-node.md)
- [ ] Read [passport-email.md](./passport-email.md) if changing send-code, register verify, forget, or SMTP apply
- [ ] Read [system-config.md](./system-config.md) if changing `ConfigService` defaults, save/merge, nested readers, or admin config groups
- [ ] Read [server-node.md](./server-node.md) if changing obfuscated node paths, `server_token`, node intervals/thresholds, or v2node `install_command`
- [ ] v2node install: script **and** Release assets from `Jireh012/v2node` (never `wyx2685` binaries); `--api-host` = API origin not Vite
- [ ] Background jobs / queue monitor → [job-queue.md](./job-queue.md); apply `v2_job_failed.sql`; do not claim Horizon
- [ ] Nested config: read via `getStringFromGroup` / section getters — never top-level flat keys
- [ ] Read [error-handling.md](./error-handling.md) — do not swallow conversion errors in list aggregators
- [ ] Nested config defaults: **no** `Map.of` for merge targets
- [ ] Confirm durable job states have startup recovery if paired with in-memory locks
- [ ] Prefer `ConfigService.buildSubscribeUrl` over raw `@Value` for subscribe links
- [ ] Money = cents; plan `transfer_enable` = **GB**; nullable clears need `LambdaUpdateWrapper`

---

## How to Fill These Guidelines

1. Document the project's **actual** conventions (not ideals)
2. Include code examples from this codebase
3. List forbidden patterns and why
4. Prefer the 7-section Scenario template for infra / cross-layer contracts

**Language**: All documentation in this directory should be written in **English**.
