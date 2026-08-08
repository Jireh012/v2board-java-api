# 工单状态配置读库生效

## Goal

管理端「工单状态」保存后立即按 DB 控制用户开单；关闭时用户端隐藏发起入口。

## Requirements

1. `ticket.ticket_status`：0 全部可用；1 仅有已完成/折抵订单用户；2 禁止开单。  
2. `TicketController#save` 读 ConfigService（DB），非仅 yml。  
3. 用户端可获知 `ticket_status`（如挂在 getSubscribe）；`TicketView` 在 status=2 时隐藏「发起新工单」。  
4. status=2 时提现开单（withdraw）一并拒绝。

## Acceptance Criteria

| ID | Criterion |
|----|-----------|
| AC1 | 管理端改为 2 并保存后，用户 save 返回关闭错误 |
| AC2 | 改为 1 后，无付费订单用户无法开单；有 status 3/4 订单可开 |
| AC3 | 改为 0 后任意登录用户可开单 |
| AC4 | status=2 时 TicketView 不展示发起按钮 |

## Out of Scope

- 已有工单的回复/关闭（仍可用）  
- Telegram 工单通知  
