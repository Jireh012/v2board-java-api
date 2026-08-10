# Design: Quantumult X / Loon ACL4SSR

## Templates

- `rules/default.quantumultx.conf`：`[policy]` + `[server_local]` + `[filter_local]`
- `rules/default.loon.conf`：Surge 同构 `[Proxy]` / `[Proxy Group]` / `[Rule]`

占位符与 Surge 对齐：`$proxies`、`$proxy_group`、`$proxy_group_{hk,tw,sg,jp,us,kr,nf}`、`$subs_domain`（Loon 另有 `$subs_link`）。

## Rules

全部内联 DOMAIN / host-suffix / GEOIP；**禁止** `filter_remote` / `RULE-SET` / GitHub raw URL。

## Builder

- 保留协议行生成；`buildFromContent` 填模板并调用 `ConfTemplatePlaceholders.applyProxyGroups`
- QX `[policy]` 空地区组按「无 peer、仅剩参数」剪枝；Loon 复用 `[Proxy Group]` 剪枝

## Handler

`RuleTemplateService.resolve("quantumultx"|"loon")` → `buildFromContent`；响应附带 `.conf` disposition。
