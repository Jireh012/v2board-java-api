# 完善 Telegram Bot 功能

## Goal

打通管理端 Telegram 配置：Webhook 可收消息；用户可绑定/查流量；工单与支付通知可达管理员；群组链接可展示。

## Requirements

1. `POST /api/v1/guest/telegram/webhook?access_token=md5(token)` 对齐 PHP Guest\\TelegramController。  
2. 指令（私聊）：`/bind <订阅URL>`、`/unbind`、`/traffic`、`/getlatesturl`；管理员私聊回复 `#工单ID` 文本回复工单。  
3. `chat_join_request`：已绑定且可用用户审批，否则拒绝。  
4. 用户开单 / 管理员回复工单 → `sendMessageWithAdmin`（staff 可选）；用户有 telegram_id 时管理员回复通知用户。  
5. `sendMessageWithAdmin(isStaff)`：`true` 时含 is_staff；enable 正确识别 0/1。  
6. 公开配置下发 `telegram_discuss_link`；个人中心展示链接 + 解绑 Telegram。  
7. 「设置 Webhook」依赖站点 `app_url`；失败返回明确错误。

## Acceptance Criteria

| ID | Criterion |
|----|-----------|
| AC1 | 设置 Webhook 指向 guest webhook；错误 token 401 |
| AC2 | /bind 合法订阅链接后 user.telegram_id 写入 |
| AC3 | /traffic 返回流量摘要 |
| AC4 | 开单后管理员 TG 收到提醒（enable+已绑定） |
| AC5 | 个人中心可见群组链接并可解绑 |

## Out of Scope

- 完整 PHP 插件热加载  
- 群聊内除指令外的复杂管理  
