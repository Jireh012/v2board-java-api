# Implement: 同步 ACL4SSR Online 并内联

## Checklist

1. [x] 新增 `Acl4ssrIniParser` + 单测（Online Full NoAuto 样例片段：ruleset/geoip/final/custom_proxy_group）
2. [x] 新增 list/provider 拉取辅助（复用 `ExternalSubscribeFetcher`；必要时提高大 IP list 上限或专用方法）
3. [x] 新增 `Acl4ssrTemplateMaterializer`：clash/stash 全量内联 + 壳保留
4. [x] Materializer：surge / surfboard / loon / quantumultx（参考 `bake_inline_rules.py` 映射）
5. [x] Materializer：singbox（outbounds + route.rules 内联，无 remote rule_set）
6. [x] 新增 `ClashRuleProviderExpander`（YAML + HTTP providers）
7. [x] 改造 `RuleTemplateService.sync`：默认 URL、INI/YAML 分支、失败不落库；更新 `sync_hint`
8. [x] `RuleTemplateService.fetch`：无 source_url 时展示默认 Online URL（便于 UI）
9. [x] UI：`AdminSubscribeRuleView.vue` 文案/placeholder/hint
10. [x] 单测：`RuleTemplateServiceTest` sync Online INI（mock fetcher 多 URL）；list 失败不 persist；产物无 raw URL
11. [x] 更新 `.trellis/spec/backend/subscribe-rules.md` Sync/Sanitize 产品说明
12. [x] 全量相关测试：`RuleTemplateSanitizerTest`、`RuleTemplateServiceTest`、各 Builder 烟雾（产物无远程依赖）

## Validation

```bash
mvn -Dtest=RuleTemplateServiceTest,RuleTemplateSanitizerTest,Acl4ssrIniParserTest,Acl4ssrTemplateMaterializerTest,ClashRuleProviderExpanderTest,ClashMetaBuilderTest,SingboxBuilderTest,SurgeBuilderTest,SurfboardBuilderTest,QuantumultXBuilderTest,LoonBuilderTest test

# 可选手工：对本地/测试库 POST sync format=clash url=<默认 Online>
# 检查返回 content 无 rule-providers / raw.githubusercontent.com，且含大量 DOMAIN-SUFFIX
```

## Risky files

- `RuleTemplateService.java` — sync 编排
- `RuleTemplateSanitizer.java` — 仅文案/断言配合，逻辑尽量不动
- `ExternalSubscribeFetcher.java` — 字节上限
- `AdminSubscribeRuleView.vue` — 文案
- `.trellis/spec/backend/subscribe-rules.md` — 契约翻转（Online 从「勿用」→「可同步并内联」）

## Rollback points

- 完成步骤 7 前：无行为变化
- 步骤 7 后异常：恢复 sync 旧逻辑 + UI 文案即可；DB 可用「恢复默认」清除大模板
