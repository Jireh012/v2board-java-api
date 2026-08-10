# Design: Surge/Surfboard ACL4SSR

## Template

`[Proxy Group]` 增加与 Clash 对齐的 select/url-test/fallback 组；`[Rule]` 将 Apple/Google/… 段改指向对应组，末尾 FINAL 指向漏网之鱼。

## Builder

`SurgeBuilder` / `SurfboardBuilder`：

- 保留 `$proxies` / `$proxy_group` 用于「手动切换」「自动」类。
- 新增地区占位（如 `$proxy_group_hk`）或在 Builder 内扫描模板标记生成过滤列表——实现时选改动面更小的一种。

Surfboard 语法与 Surge 接近，尽量共用分组表，差异单独处理。
