# 节点 API 去特征路径与 SM4

## Goal

节点侧通讯去掉默认 V2Board/UniProxy 路径与明文特征：可配置中性前缀 + 短 action；**身份 query 与业务体均 SM4**；SM4 使用现有「通讯密钥」`server_token`（不另建密钥）；与 v2node 对齐；旧特征路径硬关闭。

## Background

- 当前路径：`/api/v2/server/config`、`/api/v1/server/UniProxy/{user,push,alive,alivelist}`；query 明文：`token` / `node_id` / `node_type`。
- 已有管理端「通讯密钥」=`server.server_token`（≥16，可一键生成）。
- 复用 `Sm4Util` 算法族；**禁止**复用公开 `SM4_KEY` / `VITE_SM4_KEY`。
- 动态路由先例：`SubscribeRouteRegistrar`。本任务跨仓：`v2board-java-api`、`v2board-ui`、`v2node`。

## Key Decisions

| Decision | Choice |
|----------|--------|
| MVP | **A**：可配置前缀 + 中性短路径；体 SM4；旧路径关闭 |
| SM4 密钥 | **通讯密钥**：不新建 `server_node_sm4_key`；由 `server_token` / v2node `ApiKey` 派生 16 字节工作密钥 |
| Query 机密性 | **加密**：单一密文参数 `e` |
| 管理端 | 仅新增 **API 前缀**（空则自动生成）；通讯密钥沿用现有 UI |

## Requirements

1. **R1 路径**：`server.server_api_prefix`；action：`c`/`u`/`p`/`a`/`l`。旧 UniProxy 与 `/api/v2/server/**` 不再注册。
2. **R2 体 SM4**：POST 请求体与成功响应体为 SM4 信封；新路径无 msgpack。工作密钥 = `SHA-256(UTF-8(server_token))` 前 16 字节（面板与 v2node 一致）。
3. **R3 Query SM4**：仅参数 `e`（密文身份）；明文身份 JSON：`k`=`server_token`，`i`=node_id，`t`=类型短码（`vn`=v2node）。解密后校验 `k` 与配置 `server_token` 一致。
4. **R4 配置默认**：空前缀自动生成；`server_token` 仍按现有规则校验（≥16）；无独立 SM4 配置项。
5. **R5 热更新**：前缀变更后重绑路由。
6. **R6 v2node**：配置增加 `ApiPrefix`；**不新增 Sm4Key 字段**；用 `ApiKey` 派生工作密钥；路径 + 加密 query + 加解密体。
7. **R7 UI**：系统配置「节点」页增加 API 前缀；通讯密钥说明补充「同时用于节点 SM4」。
8. **R8 文档**：硬切换；说明通讯密钥即 SM4 材料、勿与公开 SM4 混淆。
9. **R9 一键安装**：`install_command` 增加 `--api-prefix`；`--api-key` 即通讯密钥（兼 SM4）；脚本写入 `ApiPrefix`；下载源 `Jireh012/v2node`。不增加 `--sm4-key`。

## Acceptance Criteria

| ID | Criterion |
|----|-----------|
| AC1 | 仅 `{prefix}/{c,u,p,a,l}` 可用；旧特征路径不可用 |
| AC2 | 成功响应体仅为 SM4 信封；POST 明文业务体失败 |
| AC3 | query 无明文身份字段；仅 `e`；解密后 `k` 对照 `server_token` |
| AC4 | `server_token` 错误导致无法正确加解密/鉴权失败；不回退经典明文路径 |
| AC5 | 保存/进入时若前缀为空则自动生成；无独立 SM4 配置字段 |
| AC6 | 修改前缀保存后新路径生效、旧前缀失效 |
| AC7 | v2node 用 `ApiHost`+`ApiPrefix`+`ApiKey`+`NodeID` 完成五端点契约（测试；真机 defer） |
| AC8 | 管理端可编辑/生成 API 前缀；通讯密钥仍用现有「生成密钥」 |
| AC9 | 运维文档说明通讯密钥兼作 SM4 材料 |
| AC10 | `install_command` 含 `--api-prefix` 与现有 `--api-key`；`install.sh` 支持 `--api-prefix` 写入 `ApiPrefix`；无强制 `--sm4-key` |

## Parent

`08-09-panel-api-sm4-obfuscate` 子任务（节点切片）。用户/管理见兄弟任务；支付回调等明文例外见父 PRD。

## Out of Scope

- 独立 `server_node_sm4_key` / v2node `Sm4Key` 配置项
- 用户/管理 API（兄弟任务）、双路径兼容、第三方旧节点端
- query 重放时间窗、公开 `SM4_KEY` 复用为节点密钥

## Risks

- 轮换通讯密钥 = 同时轮换 SM4（节点须同步更新 `ApiKey`）。
- 硬切换：先升面板再改全部 v2node（含 `ApiPrefix`）。
