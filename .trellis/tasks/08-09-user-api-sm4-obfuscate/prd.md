# 用户端 API 路径与 SM4

## Goal

用户端（`passport` + `/api/v1/user/**`）去掉默认识别路径与明文 JSON：可配置中性前缀 + 请求/响应 SM4 + 鉴权材料加密传递；与 `v2board-ui` 用户侧对接。

## Parent

`08-09-panel-api-sm4-obfuscate`（档位 3；支付/Telegram 等明文例外由父清单约束）。

## Requirements

1. 可配置 `site.passport_api_prefix` / `site.user_api_prefix` 替换经典 base；保留相对 path。
2. 引导配置 `site.public_config_path`（+ 前端 `VITE_PUBLIC_CONFIG_PATH`）提供启动用公开配置；内含上述前缀。
3. 请求/响应经 `SM4_KEY` 外层信封；鉴权用 header `X-A`（JWT 的 SM4 compact）；旧 `/api/v1/user|passport` 硬 404。
4. **不**加密：支付 notify、Telegram、订阅拉取。
5. 前端统一加解密与前缀拼接；`VITE_SM4_KEY` 必填。
6. 公开配置避免双重 SM4（整包一信封）。

## Acceptance Criteria

| ID | Criterion |
|----|-----------|
| AC1 | 新前缀下登录/常用 user API 可用；旧 `/api/v1/user|passport` 404 |
| AC2 | 业务 JSON 为信封；鉴权走 `X-A`，无经典 Bearer/auth_data 明文通道 |
| AC3 | 支付 notify、Telegram、订阅明文不变 |
| AC4 | 缺 `SM4_KEY` 失败关闭 |
| AC5 | `public_config_path` 可拉取并下发 passport/user 前缀 |

## Out of Scope

- 管理端、节点端（兄弟任务）
- 支付回调加密
