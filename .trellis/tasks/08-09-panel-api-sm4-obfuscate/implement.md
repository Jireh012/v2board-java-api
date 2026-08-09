# Implement — Parent orchestration

父任务本身不写业务代码。顺序：

1. [x] 审阅并批准各子任务 design（node / user / admin）
2. [x] node 子任务 → 实现 → check
3. [x] user 子任务 → 实现 → check
4. [x] admin 子任务 → 实现 → check
5. [x] 父级 PAC2：支付 notify `/api/v1/guest/payment`、Telegram `/api/v1/guest/telegram` 仍为经典明文映射；关键单测通过（2026-08-09）
6. [x] 运维文档总述已在 `docs/ops-panel-anti-block.md` 更新

## PAC (2026-08-09)

| ID | Result |
|----|--------|
| PAC1 | 三子任务 AC 已通过 check |
| PAC2 | guest payment/telegram 映射未改 |
| PAC3 | 前端 user/admin 已接前缀+SM4（需配置 VITE_*） |
| PAC4 | ops 文档已含前缀/密钥/回调勿加密 |
