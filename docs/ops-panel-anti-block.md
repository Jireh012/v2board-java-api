# 面板抗探测与防封锁运维基线

本文说明如何降低面板被主动扫描识别为默认 V2Board 部署的风险，以及源站被墙后的域名切换基线。  
**不承诺**永不被墙；也不改写全量 `/api/v1/**` 前缀。

相关产品能力（本仓已实现，需正确配置）：

| 能力 | 配置位置 | 作用 |
|------|----------|------|
| `secure_path` | 管理端 → 系统配置 → 安全 | 自定义后台入口路径段，避免 `/admin` 等默认识别路径 |
| `subscribe_path` | 管理端 → 系统配置 → 站点 | 自定义订阅 HTTP 路径；保存后热更新，旧路径立即失效 |
| `passport_api_prefix` / `user_api_prefix` / `admin_api_prefix` | 管理端 → 系统配置 → 站点 | 用户/管理 API 中性前缀 + 面板 `SM4_KEY` 体加密；旧 `/api/v1/passport|user|admin` 硬 404 |
| 固定 `GET /config` | 代码内置（不可配） | 未登录引导配置（SM4）；前端硬编码同路径，无需 env |
| `server_api_prefix` + 通讯密钥 | 管理端 → 系统配置 → 节点 | 节点 API 中性前缀 + SM4（由通讯密钥派生）；旧 UniProxy/`/api/v2/server` 已关闭 |
| 安全模式 `safe_mode_enable` | 管理端 → 系统配置 → 安全 | 未登录仅开放登录/注册/找回等必要页 |
| 站点名 `app_name` | 管理端 → 系统配置 → 站点 | 前端标题与支付默认商品名等对外文案 |

---

## 1. 藏源站（CDN / 反代）

目标：扫描器与封锁系统只看到边缘节点，**不要**把源站 IP 直接暴露给公网。

建议：

1. 源站只监听内网或仅允许 CDN/反代回源 IP；防火墙拒绝公网直连源站端口。
2. 面板与订阅入口走 CDN / 反向代理（Cloudflare、自建 Nginx + 证书等），源站不出现在 DNS A/AAAA 记录中。
3. 关闭或限制会泄露源站 IP 的旁路：邮件头、未代理的第三方回调、错误页、WebRTC、历史 DNS 记录等。
4. 管理后台尽量限制来源 IP 或额外 VPN；不要把 `secure_path` 当成唯一防护。

---

## 2. 域名拆分

建议至少拆成三类用途（可同证书、不同主机名）：

| 用途 | 示例 | 说明 |
|------|------|------|
| 落地 / 宣传 | `www.example.com` | 可不挂面板；被墙损失较小 |
| 面板（用户/管理 UI） | `panel.example.com` | 配合 `secure_path`、安全模式 |
| 订阅拉取 | `sub.example.com` 或独立路径 | 配置 `site.subscribe_url` / `subscribe_path`；与面板域名解耦便于单独切换 |

订阅链接使用 `subscribe_url`（可多行/逗号分隔）时，客户端只依赖订阅域名，面板域名被墙后仍可先切订阅。

---

## 3. 禁止源站直连暴露

- 不要在公开文档、客服话术、节点备注中写源站 IP。
- 支付回调、邮件链接、Telegram Webhook 等使用已反代域名，勿指向源站 IP。
- 若曾用源站 IP 做过 A 记录或证书申请，视为已泄露，应轮换 IP 并只走反代。

---

## 4. 被墙后切换步骤（基线）

1. **确认影响面**：面板域名、订阅域名、还是源站 IP。
2. **订阅优先**：在管理端更新 `subscribe_url`（及必要时 `subscribe_path`），通知用户重新导入订阅；旧路径在保存自定义 `subscribe_path` 后即失效。
3. **面板域名**：DNS 切到新域名 → CDN/证书就绪 → 更新 `app_url` → 用户改用新入口；`secure_path` 可一并轮换。
4. **源站 IP**：换机器或换 IP → 仅允许新回源 → 旧 IP 下线；检查是否仍有直连入口。
5. **安全模式**：对外高风险期可开启 `safe_mode_enable`，减少未登录可探测面。
6. **支付/邮件**：确认回调与发信域名仍可达，避免订单卡住暴露运维匆忙改配。

---

## 5. 节点 API 硬切换（面板 ↔ v2node）

本仓已关闭经典特征路径：`/api/v1/server/UniProxy/**`、`/api/v2/server/**`。节点只走：

```text
{server_api_prefix}/{c|u|p|a|l}?e=<SM4 compact>
```

要点：

1. **先升面板**：管理端「节点」确认 `server_api_prefix`（空则自动生成）与 ≥16 位通讯密钥。
2. **再改全部 v2node**：`config.json` 写入相同 `ApiHost`、`ApiKey`（=通讯密钥）、`ApiPrefix`（=`server_api_prefix`）、`NodeID`。
3. **SM4 材料**：工作密钥 = `SHA-256(UTF-8(通讯密钥))` 前 16 字节；**不要**与公开站点配置的 `SM4_KEY` / `VITE_SM4_KEY` 混用。
4. **一键安装**示例（含 `--api-prefix`，无 `--sm4-key`）：

```bash
wget -N https://raw.githubusercontent.com/Jireh012/v2node/main/script/install.sh && bash install.sh \
  --api-host '<面板URL>' --node-id <id> --api-key '<通讯密钥>' --api-prefix '<server_api_prefix>'
```

5. 轮换通讯密钥 = 同时轮换节点 SM4：须同步更新各节点 `ApiKey`。

---

## 6. 用户/管理端 API 硬切换（面板 ↔ v2board-ui）

用户 Passport / User / Admin API 已关闭经典路径：`/api/v1/passport/**`、`/api/v1/user/**`、`/api/v1/admin/**` → 404。浏览器只走：

```text
{passport_api_prefix}/…   {user_api_prefix}/…   {admin_api_prefix}/…
```

请求/响应 JSON 为面板 `SM4_KEY` 信封；鉴权 header `X-A`（JWT 的 SM4 compact），不使用明文 `Authorization` / `auth_data`。

引导配置：

1. 固定 `GET /config`（代码内置）；反代须把 `/config` 转到 API。
2. 管理端「站点」确认或生成 `passport_api_prefix` / `user_api_prefix` / `admin_api_prefix`（空则自动生成）。
3. 前端构建仅需 `VITE_SM4_KEY`（= `SM4_KEY`）；UI 先 GET `/config` 拿到前缀再访问登录与用户/管理 API。
4. `admin_api_prefix` 与 UI 入口 `secure_path` 无关；勿混用。

**明文例外（勿改到加密前缀下）**：支付 notify `/api/v1/guest/payment/notify/**`、Telegram webhook、订阅拉取路径。

---

## 7. 与本仓配置的配合清单

部署或巡检时勾选：

- [ ] `secure_path` 已改为 ≥8 位非保留段，且未对外传播旧路径
- [ ] `subscribe_path` 已自定义（勿长期依赖默认 `/api/v1/client/subscribe`）；保存后用新路径实测，旧默认路径应 404
- [ ] `GET /config` 可拉取公开配置（含 passport/user/admin 前缀）；反代已转发 `/config`
- [ ] `passport_api_prefix` / `user_api_prefix` / `admin_api_prefix` 已确认；旧 `/api/v1/passport|user|admin` 404
- [ ] `VITE_SM4_KEY` = 面板 `SM4_KEY`；用户/管理端业务 JSON 为信封；鉴权走 `X-A`
- [ ] `server_api_prefix` 已确认；节点 `ApiPrefix` 一致；旧 UniProxy 路径 404
- [ ] 通讯密钥 ≥16；节点 `ApiKey` 一致；勿把公开 `SM4_KEY` 当作节点密钥
- [ ] 支付 notify / Telegram / 订阅仍为明文经典路径
- [ ] `subscribe_url` / `app_url` 指向反代域名，而非源站 IP
- [ ] 安全模式按需开启；公开配置 SM4 密钥仅用于混淆，不是访问控制
- [ ] 前端壳标题由 `app_name` 覆盖；支付未填 `product_name` 时使用 `app_name + " - 订阅"`
- [ ] README / 运维文档中的示例订阅 URL 仅作开发参考，生产勿照抄默认路径

---

## 8. 明确不做的事

- 不改写全量 `/api/v1/**` API 前缀（抗扫描有限收益、兼容成本高）；用户/管理为可配置 base 前缀。
- 不提供假落地页 / 整站伪装方案。
- 不涉及节点协议混淆或客户端抗 DPI。
- 不讨论攻击或探测 GFW。
- 不提供节点 / 用户 API 经典路径双轨兼容（硬切换）。

更多开发约定见 `.trellis/spec/backend/subscribe-delivery.md`（订阅路径热更新与保存校验）、`.trellis/spec/backend/server-node.md`（节点 SM4 契约）、`.trellis/spec/backend/public-site-config.md`（公开配置引导）、`.trellis/spec/backend/panel-api-sm4.md`（面板前缀改写与 Panel SM4）。
