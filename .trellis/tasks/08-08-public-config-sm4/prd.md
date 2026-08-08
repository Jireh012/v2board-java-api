# 公开配置接口 SM4 加密

## Goal

`GET /api/v1/passport/comm/config` 的业务 `data` 以 SM4-CBC 密文下发，用户端解密后再使用，避免明文配置直接暴露在网络响应中。

## Background / Confirmed Facts

- 当前接口返回明文 `{ app_name, stop_register, invite_force, email_verify, safe_mode_enable }`。
- 工程已有 BouncyCastle（支付），无 SM4 工具；UI 无加解密依赖。
- 客户端必须持有解密密钥 → **无法做到对持有前端包的人保密**，目标是传输层混淆 / 抬高抓包成本。
- 用户确认方案：独立 `SM4_KEY`（16 字节）、SM4-CBC、随机 IV、响应 `data: { iv, payload }`。

## Requirements

1. 后端用 SM4-CBC + PKCS7 加密公开配置 JSON；每次请求随机 16 字节 IV。
2. 响应仍为 `ApiResponse`：`code`/`message` 明文；`data` 为 `{ "iv": "<base64>", "payload": "<base64>" }`。
3. 密钥来自环境变量 / yml：`SM4_KEY`（恰好 16 字节 UTF-8 或 32 位 hex）；未配置时启动或调用失败要明确（见决策）。
4. 前端 `fetchPublicSiteConfig` 解密后得到原字段对象；`siteBrand` 无感消费。
5. 前端密钥：`VITE_SM4_KEY`（与后端一致）；文档说明两端必须同步。
6. 单测覆盖加解密往返。

## Acceptance Criteria

| ID | Criterion |
|----|-----------|
| AC1 | `GET .../config` 的 `data` 仅含 `iv`+`payload`（base64），无明文 `app_name` 等 |
| AC2 | 合法密钥下前端解密后字段与加密前一致 |
| AC3 | 错误密钥 / 损坏密文 → 前端降级（缓存名 / 默认标志），不白屏 |
| AC4 | 单元测试：encrypt→decrypt 往返相等 |
| AC5 | `.env.example` / 文档注明 `SM4_KEY` 与 `VITE_SM4_KEY` |

## Out of Scope

- 其它 API 加密
- 密钥协商 / 国密 TLS / SM2
- 管理端 config/fetch 加密
- 对前端打包密钥做强混淆（可选后续）

## Key Decisions

| 决策 | 选择 |
|------|------|
| 算法 | SM4-CBC + PKCS7 |
| 密钥 | 独立 `SM4_KEY`（16 bytes），不复用 APP_KEY |
| 信封 | `data: { iv, payload }` base64 |
| 缺密钥 | 后端启动失败或请求 500（推荐：缺配置时接口 500，避免静默明文回退） |
| 前端库 | `sm-crypto`（或等价） |

## Risks

- 密钥进前端包 → 仅混淆；勿当作敏感机密保护手段。
- 与未合并的公开配置字段变更需一并兼容解密后的 JSON 字段。
