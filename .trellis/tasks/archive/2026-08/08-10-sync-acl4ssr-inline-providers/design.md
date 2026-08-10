# Design: 同步 ACL4SSR Online 并内联

## Boundaries

| 组件 | 职责 |
|------|------|
| `RuleTemplateService.sync` | URL 解析（请求 → DB → 默认 Online INI）；编排 fetch → expand/materialize → sanitize → persist |
| `Acl4ssrIniParser`（新） | 解析 Subconverter INI：`ruleset=`、`custom_proxy_group=`；忽略注释与未知键 |
| `Acl4ssrListFetcher`（新或复用 fetcher） | 并行/串行拉取全部 list URL；失败 fail-fast；可抬高单响应上限以容纳 `ChinaCompanyIp.list` |
| `Acl4ssrTemplateMaterializer`（新） | 以 classpath **full** 种子为壳，替换策略组 + 规则段，输出七种方言 |
| `ClashRuleProviderExpander`（新） | 对已是 Clash YAML 的上游：读 `rule-providers` HTTP URL，拉取 payload，把 `RULE-SET,name,policy` 展开为内联规则后删除 providers |
| `RuleTemplateSanitizer` | 仍为最后门禁；成功路径应几乎不再因 Online 触发 seed fallback |
| UI `AdminSubscribeRuleView` | 默认占位 URL、文案 |

跨仓：`v2board-ui` 仅文案/占位；核心逻辑在 `v2board-java-api`。

## Default URL

```
https://raw.githubusercontent.com/ACL4SSR/ACL4SSR/master/Clash/config/ACL4SSR_Online_Full_NoAuto.ini
```

常量放在 `RuleTemplateService`（或小配置类），admin fetch 在 `is_default` 且无 `source_url` 时可回填展示用默认 URL（可选，便于一键同步）。

## Data flow

```
POST /admin/subscribe-rule/sync { format, url? }
  → resolveUrl
  → ExternalSubscribeFetcher.fetch(url)   // INI 或 YAML
  → branch:
       INI  → parse → fetch lists → Materializer(format, seed, model, lists)
       Clash YAML with rule-providers → Expander → (可选再 merge 壳)
       其他 → 原样 content
  → Sanitizer.sanitize(format, content, seed)
  → persist(update_source=sync, source_url=resolvedUrl)
```

## INI 模型

```text
ruleset=<policy>,<url|[]GEOIP,CN|[]FINAL|...>
custom_proxy_group=<name>`<type>`<member>`<member>`...
```

成员：

| 形态 | 含义 |
|------|------|
| `[]组名` | 引用策略组 / DIRECT / REJECT |
| `.*` 或正则 | 节点过滤（写入 Clash `proxies` 正则或空+Builder 填充） |
| 字面量 | 少见；按原样放入 |

`type`：`select` / `url-test` / `fallback` / `load-balance`（MVP 映射常见三者；未知 type → `select`）。

内联标记规则：

| ruleset 第二段 | 输出（Clash） |
|----------------|---------------|
| `https://...` | 解析 list → 多行 `DOMAIN-SUFFIX/DOMAIN/.../IP-CIDR,...,policy` |
| `[]GEOIP,CN` | `GEOIP,CN,policy` |
| `[]GEOIP,LAN` | `GEOIP,private,policy` 或等价 |
| `[]FINAL` | `MATCH,policy` |

Surge/QX/Loon：沿用 `bake_inline_rules.py` 的 kind→方言映射（可抽共享逻辑到 Java）。

Sing-box：保留 `dns`/`inbounds`/`experimental`；`outbounds` 中策略组按 `custom_proxy_group` 重建（保留 DIRECT）；`route.rules` 前部保留 sniff/dns/clash_mode，其后按 list 聚合成 `domain_suffix`/`domain_keyword`/`ip_cidr` 规则对象（无 remote `rule_set`）。

Stash：与 Clash 同 YAML 物化，写入 `format=stash` 行。

## 壳保留（P）

| Format | 保留 | 替换 |
|--------|------|------|
| clash/stash | mixed-port、dns、mode 等 | `proxy-groups`、`rules`；删除 `rule-providers` |
| surge/surfboard/loon | `[General]` 等非 Proxy Group/Rule 段 | `[Proxy Group]`、`[Rule]`（Loon 等价段名按种子） |
| quantumultx | 非 policy/filter 段 | policy / filter 相关段按种子结构替换 |
| singbox | dns/inbounds/experimental | policy outbounds + route.rules（分流部分） |

种子来源：`loadClasspathSeed(format)`（stash 无独立种子时用 clash）。

## YAML rule-providers 兼容路径

1. SnakeYAML 解析 → 取 `rule-providers` 中 `type: http` + `url`。
2. 拉取每个 url（payload 可能是 `payload:` YAML 或 classical list 文本）。
3. 遍历 `rules`：`RULE-SET,Name,Policy` → 展开为该 provider 的规则行。
4. 删除 `rule-providers`；再 sanitize。

若上游同时不是 INI、也无 rule-providers，则走现有「消毒保存」行为。

## Errors

| 条件 | 结果 |
|------|------|
| INI 无任何 ruleset | 500，明确错误 |
| 任一 list/provider HTTP 失败 | 500，`拉取规则列表失败：{url}: …`，不落库 |
| Materialize 后 sanitize 仍含远程依赖 | 500 或 seed fallback（应视为 bug；单测锁定成功路径不 fallback） |
| format 不支持 | 现有错误 |

## Compatibility

- 更新 `.trellis/spec/backend/subscribe-rules.md` Scenario Sanitize / Sync：Online INI **允许作为同步源**，成功路径为 expand+inline，不再把 Online 定义为「预期 seed fallback」。
- 旧文案 `sync_hint` 改为「已从 Online/raw 内联本地化」类提示。
- Builder / 订阅契约不变：输出仍禁止远程规则依赖。

## Trade-offs

| 选择 | 代价 |
|------|------|
| 全量内联 Y | 模板与订阅体积大；换取与 Online 一致的域名精度 |
| 直连 GitHub raw | 服务端网络依赖；无镜像回退（Out of Scope） |
| 七 format 一次交付 | 实现面大；用 Materializer 按 format 分支 + 单测矩阵控制 |

## Rollback

- 功能开关非必须：管理员「恢复默认」即可回 classpath 种子。
- 代码回滚：删除 expand 编排，恢复「仅 sanitize」即可。
