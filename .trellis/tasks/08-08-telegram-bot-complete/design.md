# Design

## Components

- `GuestTelegramController` — webhook + access_token check (MD5 of bot token)
- `TelegramBotHandler` — parse update; dispatch commands; join request
- `TelegramService` — expand: sync sendMessage, sendMessageWithAdmin(text, includeStaff), getMe, approve/decline join; fix enable via intFromGroup-style; POST body preferred over GET for sendMessage
- Wire notify: `TicketController.save`, `AdminTicketController.reply` (+ notify ticket owner), keep payment callback
- Public: `telegram_discuss_link` in CommController
- UI: Profile discuss link + unbind; optional Invite page link

## Bind

Parse subscribe URL query `token=` (support relative/absolute); find User by token; set telegram_id = chat_id (private only).
