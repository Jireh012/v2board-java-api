# 管理端 API 路径与 SM4

## Goal

管理端（`/api/v1/admin/**`）去掉默认识别路径与明文 JSON：可配置中性前缀 + 请求/响应 SM4 + 鉴权材料加密传递；与管理 UI 对接。

## Parent

`08-09-panel-api-sm4-obfuscate`（档位 3）。依赖或并行：用户端子任务的共享编解码可复用。

## Requirements

1. 可配置 `site.admin_api_prefix` 替换 `/api/v1/admin`；保留相对 path；与 UI `secure_path` 分离。
2. 请求/响应 SM4（`SM4_KEY`）；旧 `/api/v1/admin` 硬 404；复用 `ClientApiPathFilter` / `PanelSm4Filter` / `X-A`。
3. 公开配置下发 `admin_api_prefix`；管理前端统一加解密。
4. 勿误伤支付 notify / Telegram / 订阅明文例外。

## Acceptance Criteria

| ID | Criterion |
|----|-----------|
| AC1 | 新前缀下管理登录与常用管理 API 可用；旧 `/api/v1/admin` 404 |
| AC2 | 管理 API 为 SM4 信封 + `X-A` |
| AC3 | 支付 notify 等 guest 回调仍明文 |
| AC4 | 缺 `SM4_KEY` 失败关闭 |
| AC5 | 公开配置含 `admin_api_prefix` |

## Out of Scope

- 节点 / 用户子任务实现细节
- 支付回调加密
