# 订阅信息展示配置生效

## Goal

使管理端「节点端显示用户信息 / 订阅展示方式 / 订阅到期提前天数」按文案生效，并与订阅 URL/TOTP 生成解耦。

## Requirements

1. `show_info_to_server_enable`：订阅输出注入信息节点时读 DB（ConfigService），非仅 yml。  
2. `show_subscribe_method`：控制信息节点内容 — 0 全部（流量+重置天+到期）、1 仅到期、2 仅流量；不影响 subscribe URL。  
3. `show_subscribe_expire`：用户端「即将到期」徽章提前天数；不参与 TOTP。  
4. `buildSubscribeUrl` 默认直连 token（method=0），不再误用上述两项。

## Acceptance Criteria

| ID | Criterion |
|----|-----------|
| AC1 | 管理端关闭「节点端显示」后订阅不再注入信息节点 |
| AC2 | method=1 仅注入到期节点；method=2 仅流量；method=0 全量 |
| AC3 | 距到期 ≤ N 天且未过期时用户端显示「即将到期」 |
| AC4 | 改展示方式/提前天数不改变 subscribe_url 的 token 形态 |

## Out of Scope

- 完整 OTP/TOTP 订阅链接管理端配置  
- Clash 专属展示格式  
