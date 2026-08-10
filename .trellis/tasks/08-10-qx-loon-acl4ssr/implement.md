# Implement: Quantumult X / Loon ACL4SSR

1. [x] 编写 `default.quantumultx.conf` / `default.loon.conf`（完整策略组 + 本地规则，无远程 URL）
2. [x] 扩展 `ConfTemplatePlaceholders`：复用地区占位；支持 QX `[policy]` 空组剪枝
3. [x] `QuantumultXBuilder` / `LoonBuilder`：`buildFromContent` + 节点行填入模板
4. [x] Handler 注入 `RuleTemplateService.resolve`
5. [x] 单测：完整组、无 GitHub/filter_remote/RULE-SET、地区过滤
6. [x] `mvn -Dtest=QuantumultXBuilderTest,LoonBuilderTest,SurgeBuilderTest test`
