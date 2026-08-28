# Design — 对齐 wyx2685 上游（2026-08）

## Boundaries

| Repo | 改动 |
|------|------|
| `v2node` (Jireh012) | cherry-pick/merge `160320c` + `ad749f5`；冲突时保留 SM4、Jireh012 install、SNI 重签 |
| `v2board-java-api` | remote 证书生成、订阅 pcs、剥私钥、Paytaro、cancel CAS |
| `v2board-ui` | v2node TLS remote 选项 + PIN 只读；Paytaro 静态表单兜底 |
| 安装 | 仍 `Jireh012/v2node` Release，不指向 wyx2685 |

## A. Remote cert

PHP `V2nodeController::save`：`tls` 开且 `cert_mode===remote` 且无 `pinned_peer_cert_sha256` → OpenSSL EC P-256 自签 3650 天，PIN = SHA-256(DER) hex。

Java 放在 `AdminNodeController` 的 v2node tls 归一化（已有 Reality 补全）。算法：Java 17 `KeyPairGenerator` EC secp256r1 + 自签 X509（可用已有 BouncyCastle）。

下发：

- `V2ServerController` 现有 `tls_settings` 整包 JSON → 节点。v2node `GetNodeInfo` 在 `cert_mode=="remote"` 时拷贝 `tls_cert`/`tls_key` 并写文件（上游 patch）。
- `ServerService` 用户节点 map：`tls_settings` 去掉 `tls_cert`、`tls_key`。
- `Helper.build*Uri`：TLS 开启时写 `pcs`（空则空字符串，对齐 PHP）。

UI `AdminServersView`：`<option value="remote">自签名(面板下发)</option>`；`cert_mode==remote` 显示只读 PIN。

## B. xray-core

按上游 `go.mod` 对齐 `github.com/xtls/xray-core`。`go test` 触及的包。随后打 Jireh012 Release（实现后单独确认，不阻塞代码合入）。

## C. Paytaro

新 `PaytaroDriver`，对照 [Paytaro.php](https://github.com/wyx2685/v2board/blob/master/app/Payments/Paytaro.php)：

- 配置键：`pid`、`key`（不是 mgate_*）
- `pay`：`type=1` URL = `https://v3.paytaro.com/submit.php?` + query（`type=alipay` 固定）
- 签名：过滤空值、`ksort`、`k=v&k=v` + secret、MD5；notify 比签忽略大小写
- notify 成功：`trade_no=out_trade_no`，`callback_no=trade_no`，响应体 `success`
- `PaymentDriverFactory` + `getPaymentMethods()` **第一项** Paytaro；MGate 保留
- 表单走 `PaymentService.getFormDefinition`；UI `getStaticForm` 同步兜底
- `PanelApiActionCatalog` 无新 path

## D. Cancel CAS

`OrderService.cancel`：`LambdaUpdateWrapper` `eq id` + `eq status 0` → `status=2`（+ `updated_at`）。`update != 1` 则 false，不退余额。

## Security

订阅与 Clash 输入不得含 `tls_key`。节点 API 必须含 PEM，否则 remote 模式无法写盘。

## Tests

- 远程证书：首次生成有 PIN/PEM；第二次相同；无 tls 不生成
- 订阅 map 无 PEM；URI 含 pcs
- Paytaro 签名向量 + notify 失败路径
- cancel：status≠0 不改；并发第二次失败
