# Implement — v2node ↔ Java 契约对齐

## Checklist

1. **[x] 写契约矩阵**  
   在本任务目录创建 `contract-matrix.md`：5 端点 ×（鉴权 / query / 请求体 / 响应字段 / ETag / 状态）。  
   对照源：
   - v2node：`api/v2board/panel.go`、`node.go`、`user.go`
   - Java：`V2ServerController`、`UniProxyController`
   - PHP：wyx2685/v2board 对应 Server/UniProxy（按需拉取）

2. **[x] 标注状态**  
   每行：`ok` / `drift` / `fix-java` / `fix-v2node` / `defer`。  
   优先确认候选：`speed_limit`、`alivelist` 鉴权、`base_config` 四字段、push/alive body 形状。

3. **[x] 修 Java 漂移**（默认）  
   - `UniProxyController.user`：经 `buildUserEntry` 输出 `speed_limit`（对齐 PHP `toArray` + v2node `UserInfo`）。
   - `UniProxyController.alivelist`：调用 `resolveNodeContext`（对齐 PHP 构造器鉴权；响应仍为全局 `alive` map）。
   - `base_config` / token / push / alive / V2 config：矩阵确认已对齐，无需改 v2node。

4. **[x] 补测试**  
   - `UniProxyControllerTest`：`buildUserEntry` 含 v2node UserInfo 字段；`base_config` / token 保留。
   - `V2ServerControllerTest`：补充 `base_config` 默认四字段。

5. **[x] Spec 增量（可选但推荐）**  
   `.trellis/spec/backend/server-node.md` 追加 UniProxy user / alivelist 场景。

6. **[x] 验证**  
   ```bash
   mvn -Dtest=V2ServerControllerTest,UniProxyControllerTest test
   ```

## Validation

| Gate | Command / artifact |
|------|-------------------|
| 矩阵完整 | `contract-matrix.md` 存在且 5 端点均有状态 |
| 单测 | 上表 `mvn -Dtest=...` 通过 |
| AC 映射 | PRD AC1–AC4 可勾选 |

## Fixed / deferred

| Item | Outcome |
|------|---------|
| user `speed_limit` | fixed in Java |
| alivelist token auth | fixed in Java (follow PHP) |
| Live v2node E2E | deferred (AC4) |
| ETag/msgpack byte-identity vs PHP | deferred |

## Risky files / rollback

| File | Risk |
|------|------|
| `UniProxyController.java` | user/alive/push 行为变更影响所有节点类型 |
| `V2ServerController.java` | 仅 v2node 配置 |
| `server-node.md` | 文档漂移 |

回滚：还原上述文件对应 commit；矩阵可保留作审计痕迹。

## Before `task.py start`

- [x] `prd.md` / `design.md` / `implement.md` 齐全  
- [x] 用户决策（目标 A、验证深度 1）已写入  
- [ ] 用户明确批准本规划摘要后，再 `task.py start`
