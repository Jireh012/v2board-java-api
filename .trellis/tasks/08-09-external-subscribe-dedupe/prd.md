# 第三方订阅跨源去重与同名编号

## Goal

用户订阅输出中的第三方可达节点按逻辑身份跨源去重，避免同一节点因多源/多 TLS 变体重复出现；去重后若显示名冲突，自动追加计数后缀。同步落库指纹改为逻辑键，同源内自然收敛。

## Background（已核实）

- 现状：同源 `fingerprint`（整份 outbound JSON，含 `tag`）去重有效；跨源与「同 host:port、不同 TLS/名称」不会去重。
- 线上抽样：启用源 reachable≈378，按 `type+server+port` 约 232；订阅侧存在明显重复。
- 下发入口：`ClientController.subscribe` → `ExternalSubscribeNodeService.listReachableAsServerMaps()`；安全前缀在 Controller 侧后置添加。

## Requirements

1. **逻辑键**
   - 组成：`type + server + port + credential`
   - `credential`：优先 `uuid`；无 uuid 时用 `password`；都没有则为空串。
   - `server` 比较前小写归一；`type` 小写；`port` 规范化为数字字符串。

2. **同步指纹 = 逻辑键哈希（已定）**
   - `ExternalSubscribeParser.fingerprint` 改为基于逻辑键（非整份 outbound）。
   - 同源 upsert / `uk_source_fingerprint` 下，TLS 变体与仅改名会收敛为一条（保留解析序第一条，与现有 `dedupe`/`putIfAbsent` 一致）。
   - 下次同步后旧指纹节点会被「未出现」清理逻辑删除。

3. **跨源去重（订阅下发，已定）**
   - 在 `listReachableAsServerMaps`（或等价私有步骤）按逻辑键去重。
   - 同键保留合并序第一条：查询序 `sort ASC, id ASC`。
   - 管理端按源节点列表**不**额外折叠，展示库内现状。

4. **同名编号（已定）**
   - 去重后的下发列表上，若显示名完全相同，追加 `1`、`2`…：`原名1`、`原名2`。
   - 独有名称不加后缀。
   - 编号发生在安全前缀（`⚠️ `）之前；同步更新 `name` / `clash_proxy.name` / `singbox_outbound.tag` / `share_uri` 片段名（与 `applyNodeSecurityMarkers` 契约一致）。

## Out of Scope

- 不改面板自有节点去重。
- 不新增订阅源优先级配置。
- 不做一次性手工清库脚本（依赖下次同步收敛）。

## Acceptance Criteria

- [x] 多源相同 `type+server+port+uuid/password` 可达节点时，用户订阅只出现一条。
- [x] 同源内仅 TLS/tag 不同、逻辑键相同的节点，同步后库内收敛为一条；订阅输出一条。
- [x] 冲突保留合并序第一条（`sort ASC, id ASC`）。
- [x] 去重后同名 → `原名1`、`原名2`…；独有名不加数字。
- [x] `⚠️ ` 前缀仍正确加在最终显示名上，且 clash/singbox/share_uri 同步。
- [x] 单元测试覆盖：逻辑键、同步指纹收敛、跨源保留序、同名编号。

## Decisions

| # | Decision | Choice |
|---|----------|--------|
| 1 | 冲突保留 | 合并序第一条（`sort ASC, id ASC`） |
| 2 | 管理端列表 | 不额外折叠 |
| 3 | 同步 fingerprint | 改为逻辑键（库内收敛 + 少探测） |

## Notes

- 复杂任务：`design.md` + `implement.md` 齐备后，用户批准本规划摘要方可 `task.py start`。
