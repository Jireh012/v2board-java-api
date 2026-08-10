# Implement: 多客户端 ACL4SSR + 管理端（父）

## Checklist

1. [x] 用户批准本最终规划后，按子任务 `task.py start`
2. [x] DDL `v2_subscribe_rule_template` + `RuleTemplateService`（resolve/Redis）
3. [x] Clash/Stash 完整模板 + Builder 合并语义 + 单测
4. [x] Surge/Surfboard
5. [x] Sing-box（无远程 rule_set）
6. [x] QX/Loon 完整配置 Builder + 种子
7. [x] SanitizePipeline + Admin API
8. [x] UI「订阅规则」
9. [x] Spec：`subscribe-delivery` / 新建 `subscribe-rules.md`
10. [x] 父级集成验收 PAC1–PAC4（check 阶段）

## Validation

```bash
mvn -Dtest=ClashMetaBuilderTest,SingboxBuilderTest,SurgeBuilderTest,SurfboardBuilderTest,QuantumultXBuilderTest,LoonBuilderTest,RuleTemplateSanitizerTest,RuleTemplateServiceTest test
# 各 format build 产物：grep -iE 'raw.githubusercontent|rule-providers:|type:\s*remote' 应无业务依赖
```

## Gate

- [x] 用户明确批准本摘要后才改产品代码
