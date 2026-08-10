# 同步 ACL4SSR Online 并内联 rule-providers

## Goal

管理员在「订阅规则」页对各客户端 format 一键同步 ACL4SSR Online Full NoAuto 时，服务端拉取 `.ini` 与全部 GitHub raw `.list`，**全量内联**进本地模板后入库；用户订阅正文无远程规则依赖。

## Background

- 现网同步会剥离 `rule-providers` / raw URL 并用种子补齐，同步 Online 实质无效。
- 官方 Online 源为 Subconverter INI：`Clash/config/ACL4SSR_Online_Full_NoAuto.ini`（`ruleset=` + `custom_proxy_group=`）。
- 内容列 `LONGTEXT`，可承载全量内联后的大模板。
- 管理端 UI 当前劝阻 Online；需改为支持并说明服务端内联。

## Decisions

| 决策 | 结论 |
|------|------|
| 默认同步 URL | `https://raw.githubusercontent.com/ACL4SSR/ACL4SSR/master/Clash/config/ACL4SSR_Online_Full_NoAuto.ini` |
| 能力 | 服务端拉取并内联；客户端永不拉 GitHub |
| Format 范围 | clash / stash / surge / surfboard / singbox / quantumultx / loon |
| 规则物化 | **全量内联** `.list`（Clash 不用 GEOSITE 近似替代本次同步结果） |
| 壳配置 | **保留种子壳**（DNS/端口/`[General]`/inbounds 等）；只替换策略组 + 规则段 |
| URL 解析 | 请求 URL → 已存 `source_url` → 上述默认 Online INI |

## Requirements

1. `sync` 识别 ACL4SSR Online `.ini`，解析 `ruleset=` / `custom_proxy_group=`，拉取全部远程 `.list`（任一条失败则整次失败）。
2. 按目标 format 将策略组与规则全量写入种子壳对应段，再经现有 sanitize 门禁后入库。
3. 兼容上游已是含 HTTP `rule-providers` 的 Clash/Stash YAML：拉取各 provider 并内联后同样入库。
4. 七个 format 均可用同一默认 Online INI 同步生成方言模板。
5. 管理端默认占位/说明改为推荐该 Online URL，并标明「服务端内联本地化」。
6. `simple` / `nodes` 档位仍只读 classpath，不受本次同步影响。

## Acceptance Criteria

- [ ] AC1：七个 format 各自对默认 Online INI 同步成功后，DB 内容无 `raw.githubusercontent.com` / `rule-providers` / remote `rule_set` 规则依赖，且规则来自内联展开（`used_seed_fallback` 不为因 Online 而整份回落）。
- [ ] AC2：留空 URL 时：有 `source_url` 用其，否则用默认 Online INI。
- [ ] AC3：同步后各 format Builder 产物仍无远程规则依赖。
- [ ] AC4：任一 `.list` 拉取失败 → 明确 `BusinessException`，不落库。
- [ ] AC5：UI 文案/占位符与默认同步行为一致。
- [ ] AC6：同步后 Clash/Stash 模板仍保留种子中的 DNS/端口等壳字段；Surge 等保留 `[General]`（或等价壳）。

## Out of Scope

- 客户端自行拉取 GitHub / jsDelivr 规则。
- Subconverter 全量特性（emoji 过滤、外部 `clash_rule_base` 整页替换、脚本规则等）。
- 修改 `?rule=simple|nodes` 语义。
- 自动镜像回退（jsDelivr）——可后续增强；MVP 直连 raw（与默认 URL 一致）。

## Risks

- 全量内联后模板可达数 MB：订阅体积与 Redis 缓存变大；需关注拉取单 list 的字节上限（现 `ExternalSubscribeFetcher` 8MB）。
- 服务端需能访问 `raw.githubusercontent.com`。
