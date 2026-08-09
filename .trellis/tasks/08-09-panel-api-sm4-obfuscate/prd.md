# 面板 API 去特征与 SM4（总）

## Goal

降低默认 V2Board API 路径与明文 JSON 特征：对**节点 / 用户 / 管理**通讯做中性路径 + SM4（体与敏感身份参数）；**第三方回调类接口保持明文**，以免支付/机器人等无法对接。

## Task Map

| Child | Scope |
|-------|--------|
| `08-09-node-api-sm4-obfuscate` | v2node：前缀 + 短 action；query/体 SM4；通讯密钥派生；一键安装 |
| `08-09-user-api-sm4-obfuscate` | `passport` + `user`：可配置前缀；请求/响应 SM4；鉴权材料不进明文 query/header 惯例名 |
| `08-09-admin-api-sm4-obfuscate` | `admin`：同上（含 `secure_path` UI 段配合） |

父任务负责跨子任务验收与明文例外清单；**不**直接改业务代码（除非共享编解码落在父设计、由首个子任务落地）。

## Key Decisions

| Decision | Choice |
|----------|--------|
| 用户/管理档位 | **3**：中性路径 + 体 SM4 + 身份/敏感参数加密 |
| 节点 | 既有规划（通讯密钥派生、短 action、`e`） |
| 支付等回调 | **明文例外**（见下） |
| 用户/管理密钥 | 复用面板 `SM4_KEY` / `VITE_SM4_KEY`（与公开配置同钥；防扫描≠防逆向） |
| 用户/管理路径形态 | **可配置 base 前缀**替换 `/api/v1/user`、`/api/v1/admin`、`/api/v1/passport`；**保留**其后相对 path（不把每个接口收成单字母，避免不可维护） |

## Plaintext Allowlist（必须保持经典明文）

第三方或非浏览器客户端按固定 URL/表单回调，不得包 SM4 信封：

| Path / 模式 | 原因 |
|-------------|------|
| `/api/v1/guest/payment/notify/**` | 支付渠道异步通知（`PaymentCallbackController`） |
| `/api/v1/guest/telegram/**` | Telegram webhook |
| 订阅拉取路径（`subscribe_path`） | 客户端订阅协议正文，非面板 JSON API |
| （若有）其它支付同步 return URL 若走独立 guest 路由 | 同支付例外 |

明文例外路由**不得**挂到用户/管理加密前缀下；升级后回调 URL 配置保持上述路径（或文档标明勿改）。

## Cross-child Acceptance

| ID | Criterion |
|----|-----------|
| PAC1 | 三子任务各自 AC 通过 |
| PAC2 | 支付 notify、Telegram webhook 仍为明文且功能可用（契约/集成或手工清单） |
| PAC3 | 浏览器用户端与管理端在仅配置新前缀 + `SM4_KEY` 下可登录与常用操作 |
| PAC4 | 运维文档含：前缀/密钥、节点一键安装、回调 URL 勿加密说明 |

## Out of Scope（父级）

- 假落地页、抗 DPI、攻击探测
- 为每个 user/admin 方法做单字母短码映射
- 支付通道协议本身改造

## Open Questions

- （无阻塞）实现顺序建议：共享 SM4 filter → **node** → **user（含 passport）** → **admin**。
