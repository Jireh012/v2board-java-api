# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Java reimplementation of V2Board (originally PHP/Laravel VPN subscription panel). Designed to coexist with the PHP version, sharing the same MySQL database and Redis instance. The project provides subscription link generation, user/server management, order/payment processing, and a UniProxy node API.

**Tech stack**: Spring Boot 3.1.5, Java 17, MyBatis-Plus 3.5.4.1, MySQL 8.0+, Redis (Lettuce)

## Build & Run Commands

```bash
mvn clean package                                    # Build JAR
mvn spring-boot:run                                  # Run with Maven
java -jar target/v2board-java-api-1.0.0.jar          # Run packaged JAR
mvn test                                             # Run all tests
mvn -Dtest=UserAuthControllerTest test               # Run a single test class
mvn -Dtest=UserAuthControllerTest#checkLogin_shouldReturnOk test  # Run a single test method
```

Configuration is via environment variables (DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD, REDIS_HOST, REDIS_PASSWORD, APP_KEY, SUBSCRIBE_PATH, etc.) or Spring profiles (`application-dev.yml`).

## Architecture

### PHP Compatibility Layer

This is the most important architectural concept. The Java app shares the MySQL database (tables prefixed `v2_`) and Redis with the PHP V2Board instance:

- **Redis DB0**: Java-side cache (sessions, general). Uses `RedisTemplate` with JSON serializer.
- **Redis DB1**: PHP-compatible cache (node status, alive IPs). Uses `phpCacheRedisTemplate` with `StringRedisSerializer` and `PhpSerializeUtil` for reading/writing PHP `serialize()` format.
- **JWT keys**: Derived from Laravel's `APP_KEY` (`app.key` in application.yml) for cross-system token compatibility.
- **JSON output**: Global `SNAKE_CASE` naming via `JacksonConfig` to match PHP API contract.

### Request Flow & Authentication

Two interceptor chains configured in `WebConfig`:
- **`ClientTokenInterceptor`**: Validates `?token=` param for subscription endpoints. Loads `User` into request attributes.
- **`ClientAuthInterceptor`**: Validates JWT from `Authorization: Bearer` header or `?auth_data=` param. Applied to `/api/v1/user/**` and `/api/v1/admin/**`.

### Dynamic Route Registration

The subscription endpoint path is configurable via `v2board.subscribe-path`. `DynamicRouteConfig` listens for `ContextRefreshedEvent` and uses `RequestMappingHandlerMapping` to register `ClientController.subscribe()` at runtime via reflection.

### Protocol Handler Pattern

Subscription format output is pluggable via `ProtocolHandler` interface:
- `getFlag()` → protocol identifier string
- `handle(User, List<Map>) → String` subscription content

Implementations: `GeneralHandler` (Base64-encoded multi-protocol URIs), `ClashHandler` (YAML, incomplete). New formats are added by implementing the interface and annotating with `@Component`.

### Multi-Protocol Server Support

8 server types: VMess, Shadowsocks, Trojan, Hysteria, VLESS, TUIC, AnyTLS, V2node. `ServerService` fetches all available servers filtered by user group, display status, and sort order.

### UniProxy Node API

`/api/v1/server/UniProxy/*` — node-side API for user list retrieval, traffic push, alive IP tracking, and node configuration. Supports both JSON and MessagePack (`jackson-dataformat-msgpack`) response formats with ETag-based caching.

### Payment Integration

`PaymentService` implements Alipay F2F (QR code via RSA2 signing with BouncyCastle) and MGate (aggregated payment). Payment callbacks arrive at `/api/v1/guest/payment/notify/{method}/{uuid}`.

## Key Package Layout

```
com.v2board.api
├── common/        ApiResponse<T> envelope, BusinessException
├── config/        WebConfig, RedisConfig (3 templates), JacksonConfig, DynamicRouteConfig, GlobalExceptionHandler
├── controller/
│   ├── ClientController          Subscription endpoint (dynamically routed)
│   ├── passport/                 Auth (login)
│   ├── user/                     User-facing API (10 controllers)
│   ├── admin/                    Admin API (8 controllers + server/ subdirectory for each protocol)
│   ├── payment/                  Payment callbacks
│   └── server/                   UniProxy node API
├── middleware/    ClientTokenInterceptor, ClientAuthInterceptor
├── model/         22 entity classes (@TableName("v2_*"), Lombok @Data)
├── mapper/        22 MyBatis-Plus BaseMapper interfaces (no custom XML)
├── protocol/      ProtocolHandler interface + GeneralHandler, ClashHandler
├── service/       UserService, ServerService, AuthService, CacheService, NodeCacheService, ConfigService, OrderService, PlanService, PaymentService
└── util/          Helper (UUID/Base64/TOTP/traffic utils), PhpSerializeUtil (PHP serialize/unserialize in Java)
```

## Database

No migration framework. Tables are created/managed by the PHP V2Board instance. All tables use the `v2_` prefix. The only Java-specific table is `v2_system_config` (DDL in `src/main/resources/db/v2_system_config.sql`).

Entity fields use camelCase in Java, mapped to snake_case columns via MyBatis-Plus `map-underscore-to-camel-case: true`.

## Conventions

- **Response format**: All API responses use `ApiResponse<T>` with fields `{code, message, data}`. Code `0` = success.
- **Errors**: Throw `BusinessException(code, message)` — caught by `GlobalExceptionHandler`.
- **Entity pattern**: Lombok `@Data` + MyBatis-Plus `@TableName`. No custom mapper XML — all queries use MyBatis-Plus built-in methods.
- **Redis config**: `V2boardRedisProperties` holds `cache-database` and `prefix` under `v2board.redis.*`. Three `RedisTemplate` beans exist: default (DB0, JSON), `cacheRedisTemplate` (DB1, JSON), `phpCacheRedisTemplate` (DB1, String for PHP compat).
- **Config merging**: `ConfigService` reads from `v2_system_config` table and deep-merges with defaults from `application.yml` under `v2board.*`.
