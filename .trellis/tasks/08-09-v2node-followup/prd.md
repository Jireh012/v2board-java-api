# v2node 节点端后续改造

## Goal

保证 [Jireh012/v2node](https://github.com/Jireh012/v2node) 与 Java 面板节点 API（V2Server config + UniProxy user/push/alive/alivelist）契约一致；以契约矩阵 + 面板侧自动化测试/修复闭环本轮交付，不强制真实节点联调。

## Background

- 本地仓：`/Users/jirehlam/Repos/v2node`（已入工作区）；fork 自 `wyx2685/v2node`。
- v2node 客户端路径（`api/v2board/*.go`）：
  | 用途 | Path |
  |------|------|
  | 节点配置 | `GET /api/v2/server/config` |
  | 用户列表 | `GET /api/v1/server/UniProxy/user`（`X-Response-Format: msgpack`） |
  | 流量上报 | `POST /api/v1/server/UniProxy/push` |
  | 在线上报 | `POST /api/v1/server/UniProxy/alive` |
  | 存活列表 | `GET /api/v1/server/UniProxy/alivelist` |
- Java：`V2ServerController`、`UniProxyController`；`base_config` / token 见 `.trellis/spec/backend/server-node.md` 与 `08-08-server-node-config`。
- 对齐基准：PHP [wyx2685/v2board](https://github.com/wyx2685/v2board)（`php-upstream.md`）。漂移优先修 Java；仅当节点偏离 PHP 时改 v2node。

## Key Decisions

| Decision | Choice |
|----------|--------|
| 本轮目标 | A — 契约对齐/联调 |
| 验证深度 | 1 — 契约矩阵 + 面板侧测试/修复（不强制起真实节点） |
| 修复落点 | 优先 Java；v2node 仅在偏离 PHP 时改动 |

## Requirements

1. **R1** 产出 5 端点契约矩阵（鉴权、query、请求/响应字段、ETag/304、msgpack），对照 v2node 客户端与 PHP/Java 实现，标注「已对齐 / 漂移 / 待修 / defer」。
2. **R2** 矩阵中必须修复的漂移落地到代码（默认 Java）；可选将稳定结论追加进 `server-node.md`。
3. **R3** 扩展或新增自动化测试覆盖：token 失败路径、`base_config` 四字段、以及矩阵中确认的关键用户/流量字段（至少含 v2node 消费的 `UserInfo` 字段集合）。
4. **R4** 本轮不做真实 v2node 进程联调；发现需环境才能确认的项记入矩阵 defer，不阻塞 AC。

## Acceptance Criteria

| ID | Criterion | Maps |
|----|-----------|------|
| AC1 | 任务目录内有完整 5 端点契约矩阵，且每项有对齐状态 | R1 |
| AC2 | 状态为「待修」且未 defer 的漂移已有对应代码变更 | R2 |
| AC3 | `mvn -Dtest=V2ServerControllerTest,UniProxyControllerTest`（或本任务新增同类测试）通过，并覆盖 AC 所述关键字段/鉴权 | R3 |
| AC4 | 无强制真实节点联调；任何环境依赖项已在矩阵标注 defer | R4 |

## Out of Scope

- 真实 v2node 端到端 smoke（验证深度选项 2）
- 仅审计不改代码（选项 3）
- 节点协议/限速算法/设备数等功能改造（选项 B）
- 安装脚本、Docker、发布流水线（选项 C）
- 相对 `wyx2685/v2node` 的上游 merge（选项 D）
- 非 v2node 专用协议节点实现细节（除非共用同一 UniProxy 路径且影响矩阵结论）

## Technical Notes (non-blocking)

规划阶段已观察到候选漂移（实施时用矩阵确认，非已决缺陷）：

- v2node `UserInfo` 需要 `speed_limit`；Java `UniProxyController.user` 当前组装用户 map 时未放入 `speedLimit`（`User` 实体有该字段）。
- `alivelist` 在 Java 上未见 `resolveNodeContext` 鉴权 — 需对照 PHP 是否故意无 token。
