# Implement — User + Passport API SM4

## Checklist

### Backend

1. [x] Config：`site.passport_api_prefix`、`site.user_api_prefix`、`site.public_config_path` 校验 + 空则自动生成；save 后刷新路由/过滤器状态
2. [x] `ClientApiPathFilter`：经典 `/api/v1/user|passport` → 404；前缀 rewrite 到内部经典 path
3. [x] `PanelSm4Filter`（或 advice）：对 rewrite 后的 user/passport 区做请求体解密、响应加密；`X-A` 解密为 JWT
4. [x] `ClientAuthInterceptor` / checkLogin：优先 `X-A`；加密区拒绝明文 `Authorization`/`auth_data`（可选严格）
5. [x] `CommController`：注册到 `public_config_path`；响应改为外层单一信封，`data` 含公开字段 + 三个 path 前缀；移除双重 SM4
6. [x] 公开配置 defaults / getters；单测覆盖前缀、404、SM4、X-A、payment 未破

### Frontend

7. [x] `.env*`：`VITE_PUBLIC_CONFIG_PATH`（与服务器一致）
8. [x] `site.ts`：从 public config path 拉配置并解密；保存 prefixes
9. [x] `http.ts`：user/passport URL 拼接；加解密 body；`X-A`；解密响应
10. [x] 所有 `/api/v1/user|passport` 调用走新 helper

### Admin UI (site 配置)

11. [x] 系统配置展示/生成三个 path 字段 + 说明（含 Vite 对齐）

### Docs

12. [x] `ops-panel-anti-block.md` + 相关 spec 增量

## Validation

```bash
# Java 17
mvn -Dtest=*UserApi*,*PanelSm4*,*ClientApi*,CommController*,ConfigService*Prefix* test

# UI typecheck if feasible (ignore unrelated AdminOrders errors)
```

## Before start

- Parent 已定档位 3；用户说「继续」→ 本子任务开工
