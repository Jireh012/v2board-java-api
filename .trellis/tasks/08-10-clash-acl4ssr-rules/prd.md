# 多客户端 ACL4SSR 分流 + 管理端规则（父任务）

## Goal

支持策略组的客户端更新订阅后，获得完整 ACL4SSR 风格分流；**规则对客户端完全本地**；管理员可在后台**一键同步上游（经改写）**、**手动编辑**、**恢复默认**各格式规则模板。

## Task Map

| 子任务 | 交付 | 顺序 |
|--------|------|------|
| `08-10-clash-stash-acl4ssr` | Clash / Stash | 1 |
| `08-10-surge-surfboard-acl4ssr` | Surge / Surfboard | 2 |
| `08-10-singbox-acl4ssr` | Sing-box（无远程 rule_set） | 3 |
| `08-10-qx-loon-acl4ssr` | Quantumult X / Loon | 4 |
| `08-10-admin-subscribe-rules` | 表 + Redis + 改写管线 + Admin API/UI | 5 |

## Key Decisions（全部已定）

| 决策 | 选择 |
|------|------|
| 风格 | ACL4SSR Full NoAuto 语义对齐 |
| 客户端规则 | 完全本地（无远程 rule-providers / rule_set URL） |
| 默认种子 | 仓库 `rules/default.*` |
| 管理能力 | **C**：可配置 URL 同步 + 手动编辑 + 恢复默认 |
| 存储 | **A**：`v2_subscribe_rule_template` + **Redis 缓存** |
| 同步改写 | **B**：剥离远程依赖并用本地种子补齐；手动保存走同一套消毒 |

## Cross-child Requirements

### R1 策略组
节点选择 / 手动 / 地区 / 苹果 / 油管 / 奈飞 / 国外媒体 / 国内媒体 / 电报 / AI / 微软相关 / 广告 / 直连 / 漏网之鱼等。

### R2 客户端本地
订阅产物不得要求客户端再拉远程规则。

### R3 模板解析
Redis → DB 自定义 → classpath 种子。写后失效缓存。

### R4 改写管线
同步与手动保存：去除远程规则依赖；缺口用对应格式本地种子中的分流段补齐；仍无法满足「无远程 URL」则拒绝保存并提示。

### R5 Builder
地区名过滤；策略引用组不塞满节点。

## Out of Scope

- 纯 URI 客户端（v2rayNG 等）
- 用户侧自选规则主题
- 完美 1:1 复刻 ACL4SSR 每一个 `.list` 域名（允许 GEOSITE 语义近似）

## Acceptance Criteria（父级）

| ID | Criterion |
|----|-----------|
| PAC1 | 五子任务各自 AC 通过 |
| PAC2 | 抽样订阅无远程规则 URL |
| PAC3 | 管理端同步/编辑/恢复可用，且立即影响新订阅 |
| PAC4 | Spec 记录存储、缓存、改写与各格式模板策略 |

## Risks

- 改写管线按格式维护成本高 → 先 Clash，再复用模式到其他格式。
- 上游结构变化导致补齐失败 → 失败保留旧模板并报错。
- QX/Loon 从纯节点升级为完整配置，客户端兼容需实测。
