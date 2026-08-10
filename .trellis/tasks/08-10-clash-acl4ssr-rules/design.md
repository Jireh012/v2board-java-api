# Design: 多客户端 ACL4SSR + 管理端规则（父）

## Architecture

```
Admin UI（订阅规则）
  ├─ GET/PUT 模板、POST sync、POST restore
  └─ RuleTemplateService
        ├─ fetch URL → SanitizePipeline(format) → DB
        ├─ manual save → SanitizePipeline → DB
        ├─ restore → delete DB row → invalidate Redis
        └─ resolve(format): Redis → DB → classpath default.*

Subscribe Builders ──► resolve(format) ──► merge nodes ──► client
```

## Storage

`v2_subscribe_rule_template`：

| 列 | 说明 |
|----|------|
| `format` | PK：`clash` / `surge` / `surfboard` / `singbox` / `quantumultx` / `loon`（stash 默认读 clash，可选独立行） |
| `content` | LONGTEXT |
| `source_url` | 同步源 |
| `update_source` | `manual` / `sync` / `restore` |
| `updated_at` | epoch |

Redis：`{prefix}subscribe:rule:{format}`，写路径主动 DEL；可读旁路长 TTL。

## SanitizePipeline（改写 B）

按 format：

1. 识别并移除远程依赖（Clash `rule-providers` URL、Sing-box remote `rule_set`、QX/Loon/Surge 的 filter/RULE-SET 远程 URL 等）。
2. 用 classpath 本地种子中对应「规则/分流」段补齐（策略组名与种子对齐；保留上游/手稿中仍合法的本地规则与组结构，冲突时以「无远程 + 可分流」为准）。
3. 校验产物：若仍含禁止的远程规则 URL → 拒绝写入。
4. 不修改节点占位符约定（`$proxies` / Builder 注入点）。

首版可对 Clash 做完整管线，其他格式「剥离 + 若过残则整份回退为本地种子并提示已本地化替换」。

## Builder 约定

见各子任务；共用：地区正则过滤、空组删、策略引用不追加全节点。

## Admin UI

`v2board-ui`：管理端新页或系统设置 Tab「订阅规则」——按格式切换、编辑器、源 URL、同步 / 保存 / 恢复默认、上次更新时间。

## Rollout

1. 表 + resolve + Redis（空表时行为=现状 classpath）  
2. Clash/Stash 种子与 Builder  
3. Surge/Surfboard → Sing-box → QX/Loon  
4. Sanitize + Admin API/UI  

## Rollback

删表数据或恢复默认；回滚 jar；清 Redis key。
