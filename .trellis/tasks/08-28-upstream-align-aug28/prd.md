# 对齐 wyx2685 上游新功能

## Goal

把 [wyx2685/v2board](https://github.com/wyx2685/v2board) 与 [wyx2685/v2node](https://github.com/wyx2685/v2node) 本月尚未对齐的新功能一次落地：**远程证书下发**、**xray-core v26.7.28**、**Paytaro**、**取消订单 CAS**。安装/Release 仍走 [Jireh012/v2node](https://github.com/Jireh012/v2node)。

## Background / Confirmed delta

已有、不搬：`trusted_x_forwarded_for`、Epusdt、Horizon 进程上限。

落后项见设计文档。PHP `MGate.php` 仍在，Paytaro 是新增并置顶，不是删除 MGate。

## Requirements

1. **远程证书（三仓）**
   - v2node：合入 `160320c`（`cert_mode=remote` 写盘），保留 SM4 / Jireh012 安装 / SNI 重签。
   - Java：v2node 保存且 `tls=1`、`cert_mode=remote`、尚无 `pinned_peer_cert_sha256` 时，生成 P-256 自签（CN=SNI，3650 天，SHA-256），写入 `tls_cert` / `tls_key` / `pinned_peer_cert_sha256`（hex）。已有 PIN 不重签。
   - 节点 config 下发完整 `tls_settings`（含 PEM）。用户订阅 map **删除** `tls_cert`/`tls_key`（同现有去掉 `encryption_settings.private_key`）。
   - 订阅 URI：`Helper` 的 VMess/VLESS/Trojan/HY2/TUIC/AnyTLS 增加 `pcs=`，对齐 PHP `25ab8e0`。Clash 等 YAML 本轮不扩（上游这次也只改 Helper）。
   - UI：证书模式增加「自签名(面板下发)」；remote 时只读展示 PIN。
2. **v2node xray-core**：合入 `ad749f5`（v26.7.28）。二进制变更后发 **Jireh012** Release（不改面板 install 指向 wyx2685）。
3. **Paytaro**：驱动 + 表单 `pid`/`key`；网关 `https://v3.paytaro.com/submit.php`；type 固定 `alipay`；MD5 签；notify `trade_status=TRADE_SUCCESS` 回 `success`。方法列表置顶 Paytaro，**保留 MGate**。
4. **取消订单**：仅 `status=0` 时更新为 2，影响行数 ≠ 1 则失败不退款（对齐 PHP `3cfb3f0` 的 OrderService）。

## Out of scope

- Horizon `HORIZON_MAX_PROCESSES`
- 删除 MGate；已对齐的 XFF / Epusdt
- 5 月及更早的订阅边角修复（除非被 pcs 改动碰到）
- 已有 remote 节点改 SNI 后自动换证（PHP 有 PIN 就不重生）

## Acceptance Criteria

- [ ] 管理端 v2node 可选 remote；首次保存生成 PIN 与 PEM；再保存不换证。
- [ ] 节点拉取 config 含 `tls_cert`/`tls_key`；订阅内容不含私钥/证书 PEM，URI 有 `pcs=`。
- [ ] Jireh012/v2node 含 remote 写盘 + xray-core v26.7.28，且仍走 SM4 ApiPrefix。
- [ ] 后台可添加 Paytaro；支付跳转与回调验签与 PHP 一致；MGate 仍可用。
- [ ] 并发取消同一待支付订单，余额只退一次。

## Technical notes

- `tls_settings` 仍是 JSON 字符串列，无 DDL。
- Paytaro notify 走现有明文 `{payment_notify_prefix}/{method}/{uuid}`。
- 远程证书生成失败 → `BusinessException`「创建失败」（PHP 文案）。
