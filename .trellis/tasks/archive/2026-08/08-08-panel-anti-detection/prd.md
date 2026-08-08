# 面板抗探测与防封锁

## Goal

降低面板站被主动扫描识别为默认 V2Board 部署的风险，并给出藏源站 IP / 域名切换的运维基线。不承诺永不被墙；不改写全量 `/api/v1/**` 前缀。

## Background

- 已有且本任务不重复实现：`secure_path`、安全模式、动态 `app_name`、公开配置 SM4、`site.subscribe_path` 热更新注册（`SubscribeRouteRegistrar`）。
- 缺口：无运维手册；`subscribe_path` 保存无校验/弱引导；`index.html` 与支付默认文案硬编码 `V2Board`；默认订阅路径仍为扫描器熟知值。

## Requirements

### R1 运维手册

1. 新增 `docs/ops-panel-anti-block.md`：CDN/反代藏源站、禁止源站直连暴露、域名拆分（落地/面板/订阅）、被墙后切换步骤、与本仓 `secure_path` / `subscribe_path` / 安全模式的配合清单。
2. 根 `README.md`（若存在）或等价入口增加指向该文档的链接。

### R2 订阅路径加固

1. 保存 `site.subscribe_path` 时校验（空允许 → 运行时默认 `/api/v1/client/subscribe`）：
   - 规范化：trim；无前导 `/` 则补上；
   - 非空须匹配安全路径字符（建议 `^/[A-Za-z0-9._~/-]+$`，禁止 `..`、查询串、空白）；
   - 不得与保留前缀冲突（至少：`/api/v1/user`、`/api/v1/admin`、`/api/v1/passport`、`/api/v1/guest`、`/api/v1/server` 及当前 `secure_path` UI 段）。
2. 非法值 → `BusinessException`，拒绝保存。
3. 管理端「订阅路径」说明：明确默认路径易被扫描，建议自定义；保存后立即生效、旧路径失效（与现热更新一致）。
4. 测试：校验拒绝非法/冲突路径；自定义路径生效后默认路径不再注册（沿用/补强现有 registrar 行为测试）。

### R3 前端壳指纹

1. `v2board-ui/index.html`：初始 `<title>` 改为中性占位（非 `V2Board`）；favicon 改为仓库内中性 SVG（非 Vite 默认标）。
2. 运行时仍由现有 `siteBrand` / `app_name` 覆盖 `document.title`（与 `08-08-site-name-dynamic` 一致）。

### R4 后端对外文案指纹

1. `PaymentService` 支付宝默认 `subject`、`WechatPayNativeDriver` 的 `body`：无自定义商品名时使用 `ConfigService.getAppName() + " - 订阅"`，不再硬编码 `V2Board - 订阅`。
2. 不要求改订阅协议构建器里作配置缺失回退的 `"V2Board"` 字符串（节点配置名回退，非公开 HTML 指纹）；若改动成本低可顺手统一，非 AC 必过项。

## Acceptance Criteria

| ID | Criterion |
|----|-----------|
| AC1 | 存在 `docs/ops-panel-anti-block.md`，覆盖藏源站、域名拆分、切换步骤，并提及 `secure_path` / `subscribe_path` / 安全模式 |
| AC2 | 保存非法 `subscribe_path`（含 `..`、冲突前缀、非法字符）被拒绝；合法自定义路径可保存 |
| AC3 | 将订阅路径改为自定义后，`GET` 新路径走订阅逻辑；`GET /api/v1/client/subscribe` 不再命中订阅 handler |
| AC4 | 管理端订阅路径表单项文案提示勿用默认识别路径 |
| AC5 | 未加载前端 JS 时，查看 `index.html` 无字面量 `V2Board` 标题，且非 Vite 默认 favicon |
| AC6 | 未配置支付 `product_name` 时，支付宝 subject / 微信 body 使用当前 `app_name` 拼接，而非固定 `V2Board - 订阅` |

## Out of Scope

- 改写 `/api/v1/**` 或管理 API 前缀（选项 C）
- 假落地页 / 整站伪装
- 节点协议混淆、客户端抗 DPI
- 攻击或探测 GFW
- 重复实现 secure_path / site name / SM4 / safe_mode 本体

## Key Decisions

| 决策 | 选择 |
|------|------|
| MVP 档位 | B：运维 + 订阅加固 + 壳/文案指纹（1+2+3+4） |
| 默认订阅路径 | 允许空/默认（兼容）；**不**在保存时强制禁止默认；用 UI 文案劝阻 |
| 运维文档位置 | `docs/ops-panel-anti-block.md` |
| 支付默认文案 | `getAppName() + " - 订阅"` |

## Risks

- 自定义 `subscribe_path` 与已有 Spring 映射冲突 → 用保留前缀列表 + 测试兜底。
- 用户改路径后旧客户端仍用旧 URL → 属运维预期；手册中写明需重新下发订阅。
