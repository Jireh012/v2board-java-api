# Design

- `ConfigService.getTicketStatus()` → `intFromGroup("ticket","ticket_status")`, fallback `@Value ticket-status` / 0.
- `TicketController`: inject ConfigService; remove field reliance on yml for gate; use getter in save + withdraw (block when ==2).
- `StatController.getSubscribe`: put `ticket_status`.
- UI: TicketView load getSubscribe (or lightweight), hide create when ===2; show toast if API rejects.
- Spec note in admin-user.md or system-config.md.
