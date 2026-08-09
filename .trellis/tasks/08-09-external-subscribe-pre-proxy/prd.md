# 第三方订阅源前置代理

## Goal

为第三方订阅源增加「前置代理」开关：开启后同步拉取时**自动**选用节点库可用节点经本地代理出站；关闭则直连。

## Confirmed

- Fetcher 直连；节点库 `reachable=1` + `singbox_outbound`；可复用 `SingBoxProbeService` 起 mixed。
- **产品**：编辑页仅开关；节点自动选择；策略 **A** = `sort ASC, id ASC` 取首个（排除本源节点）。

## Requirements

1. 源字段 `pre_proxy_enable`（0/1）；UI Toggle；关=直连。
2. 开：候选 = 其它源的 `reachable=1` 且 outbound 非空；按 `sort ASC, id ASC` 取第一个。
3. 临时 sing-box mixed → 经 HTTP Proxy 拉 `url` → 释放进程。
4. 无候选 / 代理失败 → `failed` + 可读 message；不静默直连。
5. Admin save/fetch 暴露开关；DDL + ALTER 注释。

## Out of scope

手动选节点、failover 链、面板节点前置、改 parse/探测语义。

## Decisions

| 项 | 结论 |
|----|------|
| UI | 仅开关，不选手动节点 |
| 选型 | 稳定优先 sort/id；排除本源节点 |
| 无候选 | 失败，不直连回退 |

## Acceptance Criteria

- [ ] 关：直连与现网一致。
- [ ] 开且有它源可达节点：经自动节点代理拉取成功。
- [ ] 开但无候选：同步失败消息可读；UI 仅 Toggle。
- [ ] `external-subscribe.md` 已更新。
