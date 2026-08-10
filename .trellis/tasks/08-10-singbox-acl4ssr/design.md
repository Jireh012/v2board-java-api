# Design: Sing-box ACL4SSR

## Outbounds

增加 selector：节点选择、手动、地区、苹果、油管、奈飞、国外媒体、电报、AI、广告(reject)、直连、漏网之鱼等；urltest 仅「自动选择」填全部节点。

## Route / rule_set（修订）

父任务已定：**规则对客户端完全本地**。因此 **禁止** remote `rule_set`（含 GitHub raw 与 jsDelivr/ghproxy 镜像）。

可选实现（实现时择一，优先体积可控）：

- **A**：`route.rules` 使用 Sing-box 本地可解析的 `geosite`/`geoip` 规则字段（依赖客户端自带数据库，订阅内不挂 URL）
- **B**：将关键 DOMAIN 规则内联进模板（体积更大，管理端可编辑）

推荐 **A**，不足处用少量内联 DOMAIN 补齐。

## Builder

`addProxies`：对带过滤标记的 selector 按节点名正则填充；其余策略组保持引用列表。
