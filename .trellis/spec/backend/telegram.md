# Telegram Bot

> Guest webhook + admin notifications + user bind/traffic commands. Aligns with PHP `Guest\TelegramController` and `TelegramService`.

---

## Scenario: Webhook receive & command dispatch

### 1. Scope / Trigger

- Admin「设置 Webhook」→ `POST /api/v1/admin/config/setTelegramWebhook` builds  
  `{app_url}/api/v1/guest/telegram/webhook?access_token=md5(bot_token)`  
  and calls Telegram `setWebhook` (must check JSON `ok`).
- Telegram POSTs updates to guest webhook (no JWT).

### 2. Signatures

- `GuestTelegramController#webhook` — `POST /api/v1/guest/telegram/webhook`
- `TelegramBotHandler#handleUpdate`
- `TelegramService#sendMessage` / `#sendMessageWithAdmin(text, includeStaff)` / `#setWebhook` / `#getMeUsername` / join approve|decline
- Config: `telegram.telegram_bot_enable`, `telegram.telegram_bot_token`, `telegram.telegram_discuss_link` (nested via `ConfigService`)

### 3. Contracts

| Item | Rule |
|------|------|
| `access_token` | Must equal MD5 hex of bot token; mismatch → **HTTP 401** |
| Commands (private) | `/bind <subscribeUrl>`, `/unbind`, `/traffic` |
| Anywhere | `/getlatesturl` (no private check) |
| Bind | Parse query `token=` from absolute/relative URL; set `user.telegram_id = chat_id` |
| Reply ticket | Private reply whose `reply_to_message.text` matches `#(\d+)`; admin/staff only |
| `chat_join_request` | Approve if bound + `UserService.isAvailable`; else decline |
| `sendMessageWithAdmin` | Respect enable 0/1; admins always; staff when `includeStaff=true`; require `telegram_id` |
| Public config | `telegram_discuss_link` in SM4 plain of `passport/comm/config` |

### 4. Validation & Error Matrix

| Condition | Behavior |
|-----------|----------|
| Wrong/missing access_token | HTTP 401, no handler call |
| Bot token / app_url missing on setWebhook | `BusinessException` 500 with clear message |
| Telegram API `ok=false` | `BusinessException` 500 `"来自TG的错误：{description}"` |
| Bind missing URL / bad token / already bound | Reply chat with PHP-aligned Chinese error |

### 5. Tests Required

- Unit: MD5 access token rejection/accept (`GuestTelegramControllerTest`)
- Unit: bind token extract + ticket id + format message (`TelegramBotHandlerTest`)
- Unit: enable flag Number/Boolean/String (`TelegramServiceEnableTest`)
- Unit: public config includes `telegram_discuss_link` (`CommControllerConfigTest`)

### 6. Wrong vs Correct

#### Wrong

- Treat `telegram_bot_enable` only as string `"1"` and miss boolean/`1` number.
- setWebhook ignore Telegram `ok` / empty `app_url`.
- Notify admins without `includeStaff` for ticket create/reply.

#### Correct

- `parseEnableFlag` / `intFromGroup`-style enable.
- Webhook URL depends on non-empty `site.app_url`.
- Ticket create/reply → `sendMessageWithAdmin(..., true)`.
