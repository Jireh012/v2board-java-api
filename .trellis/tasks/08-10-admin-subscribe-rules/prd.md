# 管理端订阅规则模板

## Goal

管理员管理各客户端规则模板：可配置 URL 同步（经改写 B）、手动编辑（同消毒）、恢复默认；Redis 缓存；订阅 Builder 读自定义优先。

## Parent

`08-10-clash-acl4ssr-rules`（顺序 5）

## Decisions

- 存储：`v2_subscribe_rule_template` + Redis
- 同步/保存：SanitizePipeline（剥离远程依赖 + 本地种子补齐；失败拒绝）
- UI：订阅规则管理页（Tab 或独立菜单）

## Acceptance Criteria

| ID | Criterion |
|----|-----------|
| AC1 | 各 format 可保存，新订阅使用自定义内容 |
| AC2 | 恢复默认后回退 classpath 种子 |
| AC3 | 同步上游含 GitHub rule-providers 时，入库结果无远程规则 URL 且仍可分流 |
| AC4 | 手动粘贴含远程 URL 时被消毒或拒绝，不把远程依赖下发给客户端 |
| AC5 | Redis 命中/失效行为正确（单测或集成测） |
