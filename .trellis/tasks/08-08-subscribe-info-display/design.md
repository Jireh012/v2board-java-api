# Design: 订阅信息展示配置

## Contracts

| Key | Meaning |
|-----|---------|
| `subscribe.show_info_to_server_enable` | 1 → inject info nodes into subscribe server list |
| `subscribe.show_subscribe_method` | 0=流量+重置天+到期；1=仅到期；2=仅流量 |
| `subscribe.show_subscribe_expire` | days; user UI「即将到期」 when `0 < daysLeft <= N` |

## Changes

1. `ConfigService.getShowInfoToServerEnable()` — DB via `intFromGroup`, fallback yml boolean.  
2. `ClientController` — inject `ConfigService`; gate + filter nodes by method. Reset-day node only when method=0.  
3. `buildSubscribeUrl` — always `subMethod=0` (direct token); stop passing display method/expire.  
4. `StatController.getSubscribe` — include `show_subscribe_expire`.  
5. UI `DashboardHome` — badge「即将到期」 when not expired and daysLeft ≤ N.  
6. Update yml comments to match display semantics.

## Compatibility

- Existing DB 0/1/2 values keep working under new meaning.  
- Sites that relied on admin method=2 for TOTP URLs will get plain token URLs (intentional fix).
