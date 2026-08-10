# Design: Clash/Stash ACL4SSR

见父 `design.md` Clash 节。核心：

1. 重写 `default.clash.yaml` proxy-groups + GEOSITE/GEOIP rules。
2. `mergeProxyGroup` 三分支语义。
3. 不引入 rule-providers。
